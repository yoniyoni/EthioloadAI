package com.ethioloadai.security.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class JsonRequestBodyLimitFilter extends OncePerRequestFilter {

    private static final String TOO_LARGE_RESPONSE =
            "{\"message\":\"JSON request body exceeds the configured limit\",\"code\":\"REQUEST_BODY_TOO_LARGE\"}";

    private final long maxBodyBytes;

    public JsonRequestBodyLimitFilter(long maxBodyBytes) {
        if (maxBodyBytes <= 0 || maxBodyBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("JSON request body limit must be between 1 and Integer.MAX_VALUE bytes");
        }
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isJsonRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > maxBodyBytes) {
            reject(response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes((int) maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            reject(response);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return mediaType.equals("application/json")
                || (mediaType.startsWith("application/") && mediaType.endsWith("+json"));
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(TOO_LARGE_RESPONSE);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    try {
                        if (!isFinished()) {
                            listener.onDataAvailable();
                        }
                        if (isFinished()) {
                            listener.onAllDataRead();
                        }
                    } catch (IOException exception) {
                        listener.onError(exception);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
