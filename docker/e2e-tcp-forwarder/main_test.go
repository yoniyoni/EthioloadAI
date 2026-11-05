package main

import (
	"bytes"
	"errors"
	"io"
	"net"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestForwardUsesOnlyFixedBackendAddress(t *testing.T) {
	if backendAddress != "172.26.0.3:8080" {
		t.Fatalf("fixed backend address = %q", backendAddress)
	}
}

func TestRunRejectsArgumentsAndUnknownModes(t *testing.T) {
	dialCalled := false
	dial := func(string, string, time.Duration) (net.Conn, error) {
		dialCalled = true
		return nil, errors.New("unexpected dial")
	}

	for _, args := range [][]string{
		nil,
		{"forward", "attacker.example:443"},
		{"forward", "sh"},
		{"exec"},
	} {
		if err := run(args, strings.NewReader(""), io.Discard, dial); err == nil {
			t.Fatalf("run(%q) unexpectedly succeeded", args)
		}
	}
	if dialCalled {
		t.Fatal("invalid command arguments reached the network dialer")
	}
}

func TestForwardPreservesRequestBytesAndHalfClose(t *testing.T) {
	listener := listenForTest(t)
	defer listener.Close()

	request := "POST /path?q=one HTTP/1.1\r\nContent-Length: 4\r\n\r\ntest"
	response := "HTTP/1.1 201 Created\r\nContent-Length: 2\r\n\r\nok"
	serverDone := make(chan error, 1)
	go func() {
		connection, err := listener.Accept()
		if err != nil {
			serverDone <- err
			return
		}
		defer connection.Close()
		got, err := io.ReadAll(connection)
		if err != nil {
			serverDone <- err
			return
		}
		if string(got) != request {
			serverDone <- errors.New("request bytes changed")
			return
		}
		_, err = io.WriteString(connection, response)
		serverDone <- err
	}()

	clientInput, clientPeer := net.Pipe()
	defer clientPeer.Close()
	var clientOutput bytes.Buffer
	forwardDone := make(chan error, 1)
	go func() {
		forwardDone <- forward(clientInput, &clientOutput, func(_, _ string, _ time.Duration) (net.Conn, error) {
			return net.Dial(listener.Addr().Network(), listener.Addr().String())
		})
	}()

	if _, err := io.WriteString(clientPeer, request); err != nil {
		t.Fatal(err)
	}
	_ = clientPeer.Close()

	select {
	case err := <-serverDone:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("backend did not observe the forwarded half-close")
	}
	select {
	case err := <-forwardDone:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("forwarder did not finish")
	}
	if clientOutput.String() != response {
		t.Fatalf("response bytes = %q, want %q", clientOutput.String(), response)
	}
}

func TestForwardSupportsConcurrentConnections(t *testing.T) {
	listener := listenForTest(t)
	defer listener.Close()

	const clients = 8
	var serverGroup sync.WaitGroup
	serverGroup.Add(clients)
	go func() {
		for range clients {
			connection, err := listener.Accept()
			if err != nil {
				serverGroup.Done()
				continue
			}
			go func() {
				defer serverGroup.Done()
				defer connection.Close()
				_, _ = io.Copy(io.Discard, connection)
			}()
		}
	}()

	var group sync.WaitGroup
	errorsFound := make(chan error, clients)
	for range clients {
		group.Add(1)
		go func() {
			defer group.Done()
			client, peer := net.Pipe()
			defer peer.Close()
			done := make(chan error, 1)
			go func() {
				done <- forward(client, io.Discard, func(_, _ string, _ time.Duration) (net.Conn, error) {
					return net.Dial(listener.Addr().Network(), listener.Addr().String())
				})
			}()
			_, _ = io.WriteString(peer, "independent request")
			_ = peer.Close()
			select {
			case err := <-done:
				if err != nil {
					errorsFound <- err
				}
			case <-time.After(3 * time.Second):
				errorsFound <- errors.New("concurrent forward timed out")
			}
		}()
	}
	group.Wait()
	close(errorsFound)
	for err := range errorsFound {
		t.Error(err)
	}
	serverGroup.Wait()
}

func listenForTest(t *testing.T) net.Listener {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	return listener
}
