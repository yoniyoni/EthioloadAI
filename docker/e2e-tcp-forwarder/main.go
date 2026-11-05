package main

import (
	"errors"
	"io"
	"net"
	"os"
	"os/signal"
	"syscall"
	"time"
)

const (
	dialTimeout = 5 * time.Second
)

const backendAddress = "172.26.0.3:8080"

type dialFunc func(network, address string, timeout time.Duration) (net.Conn, error)

func main() {
	if err := run(os.Args[1:], os.Stdin, os.Stdout, net.DialTimeout); err != nil {
		os.Exit(1)
	}
}

func run(args []string, input io.Reader, output io.Writer, dial dialFunc) error {
	if len(args) != 1 {
		return errors.New("exactly one fixed mode is required")
	}

	switch args[0] {
	case "serve":
		return serve()
	case "probe":
		return probe(dial)
	case "forward":
		return forward(input, output, dial)
	default:
		return errors.New("unsupported mode")
	}
}

func serve() error {
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	<-signals
	signal.Stop(signals)
	return nil
}

func probe(dial dialFunc) error {
	connection, err := dial("tcp", backendAddress, dialTimeout)
	if err != nil {
		return err
	}
	return connection.Close()
}

func forward(input io.Reader, output io.Writer, dial dialFunc) error {
	backend, err := dial("tcp", backendAddress, dialTimeout)
	if err != nil {
		return err
	}
	defer backend.Close()

	readFromBackend := make(chan error, 1)
	writeToBackend := make(chan error, 1)

	go func() {
		_, copyErr := io.Copy(output, backend)
		readFromBackend <- copyErr
	}()

	go func() {
		_, copyErr := io.Copy(backend, input)
		if tcp, ok := backend.(*net.TCPConn); ok {
			if closeErr := tcp.CloseWrite(); copyErr == nil {
				copyErr = closeErr
			}
		}
		writeToBackend <- copyErr
	}()

	select {
	case err = <-writeToBackend:
		if err != nil {
			_ = backend.Close()
			return err
		}
		err = <-readFromBackend
	case err = <-readFromBackend:
	}
	if err != nil && !errors.Is(err, net.ErrClosed) {
		return err
	}
	return nil
}
