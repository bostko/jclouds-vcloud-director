package org.jclouds.http.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMultimap;
import com.google.inject.Inject;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.jclouds.JcloudsVersion;
import org.jclouds.http.HttpRequest;
import org.jclouds.http.HttpResponse;
import org.jclouds.http.HttpUtils;
import org.jclouds.http.HttpCommand;
import org.jclouds.http.HttpResponseException;
import org.jclouds.http.HttpRequestFilter;
import org.jclouds.http.IOExceptionRetryHandler;
import org.jclouds.http.handlers.DelegatingErrorHandler;
import org.jclouds.http.handlers.DelegatingRetryHandler;
import org.jclouds.io.ContentMetadataCodec;
import org.jclouds.io.MutableContentMetadata;
import org.jclouds.io.Payload;

import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Throwables.propagate;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static com.google.common.net.HttpHeaders.USER_AGENT;
import static org.jclouds.http.HttpUtils.checkRequestHasContentLengthOrChunkedEncoding;
import static org.jclouds.http.HttpUtils.filterOutContentHeaders;
import static org.jclouds.http.HttpUtils.wirePayloadIfEnabled;
import static org.jclouds.io.Payloads.newInputStreamPayload;
import static org.jclouds.util.Throwables2.getFirstThrowableOfType;

/**
 * Created by valentin on 13/10/16.
 */
public class OkHttpCommandExecutorVcloudService extends BaseHttpCommandExecutorService<Request> {
    private static final String DEFAULT_USER_AGENT = String.format("jclouds-okhttp/%s java/%s", JcloudsVersion.get(),
            System.getProperty("java.version"));

    private final Function<URI, Proxy> proxyForURI;
    private final OkHttpClient globalClient;

    @Inject
    OkHttpCommandExecutorVcloudService(HttpUtils utils, ContentMetadataCodec contentMetadataCodec,
                                       DelegatingRetryHandler retryHandler, IOExceptionRetryHandler ioRetryHandler,
                                       DelegatingErrorHandler errorHandler, HttpWire wire, Function<URI, Proxy> proxyForURI, OkHttpClient okHttpClient) {
        super(utils, contentMetadataCodec, retryHandler, ioRetryHandler, errorHandler, wire);
        this.proxyForURI = proxyForURI;
        this.globalClient = okHttpClient;
    }

    @Override
    protected Request convert(HttpRequest request) throws IOException, InterruptedException {
        Request.Builder builder = new Request.Builder();

        builder.url(request.getEndpoint().toString());
        populateHeaders(request, builder);

        RequestBody body = null;
        Payload payload = request.getPayload();

        if (payload != null) {
            Long length = checkNotNull(payload.getContentMetadata().getContentLength(), "payload.getContentLength");
            if (length > 0) {
                body = generateRequestBody(request, payload);
            }
        }

        builder.method(request.getMethod(), body);

        return builder.build();
    }

    protected void populateHeaders(HttpRequest request, Request.Builder builder) {
        // OkHttp does not set the Accept header if not present in the request.
        // Make sure we send a flexible one.
        if (request.getFirstHeaderOrNull(ACCEPT) == null) {
            builder.addHeader(ACCEPT, "*/*");
        }
        if (request.getFirstHeaderOrNull(USER_AGENT) == null) {
            builder.addHeader(USER_AGENT, DEFAULT_USER_AGENT);
        }
        for (Map.Entry<String, String> entry : request.getHeaders().entries()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
        if (request.getPayload() != null) {
            MutableContentMetadata md = request.getPayload().getContentMetadata();
            for (Map.Entry<String, String> entry : contentMetadataCodec.toHeaders(md).entries()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    protected RequestBody generateRequestBody(final HttpRequest request, final Payload payload) {
        checkNotNull(payload.getContentMetadata().getContentType(), "payload.getContentType");
        return new RequestBody() {
            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source = Okio.source(payload.openStream());
                try {
                    sink.writeAll(source);
                } catch (IOException ex) {
                    logger.error(ex, "error writing bytes to %s", request.getEndpoint());
                    throw ex;
                } finally {
                    source.close();
                }
            }

            @Override
            public MediaType contentType() {
                return MediaType.parse(payload.getContentMetadata().getContentType());
            }
        };
    }

    @Override
    protected HttpResponse invoke(Request nativeRequest) throws IOException, InterruptedException {
        OkHttpClient requestScopedClient = globalClient.clone();
        requestScopedClient.setProxy(proxyForURI.apply(nativeRequest.uri()));

        Response response = requestScopedClient.newCall(nativeRequest).execute();

        HttpResponse.Builder<?> builder = HttpResponse.builder();
        builder.statusCode(response.code());
        builder.message(response.message());

        ImmutableMultimap.Builder<String, String> headerBuilder = ImmutableMultimap.builder();
        Headers responseHeaders = response.headers();
        for (String header : responseHeaders.names()) {
            headerBuilder.putAll(header, responseHeaders.values(header));
        }

        ImmutableMultimap<String, String> headers = headerBuilder.build();

        if (response.code() == 204 && response.body() != null) {
            response.body().close();
        } else {
            Payload payload = newInputStreamPayload(response.body().byteStream());
            contentMetadataCodec.fromHeaders(payload.getContentMetadata(), headers);
            builder.payload(payload);
        }

        builder.headers(filterOutContentHeaders(headers));

        return builder.build();
    }

    @Override
    protected void cleanup(Request nativeResponse) {

    }

    @Override
    public HttpResponse invoke(HttpCommand command) {
        HttpResponse response = null;
        for (;;) {
            HttpRequest request = command.getCurrentRequest();
            Request nativeRequest = null;
            try {
                for (HttpRequestFilter filter : request.getFilters()) {
                    request = filter.filter(request);
                }
                checkRequestHasContentLengthOrChunkedEncoding(request,
                        "After filtering, the request has neither chunked encoding nor content length: " + request);
                logger.debug("Sending request %s: %s", request.hashCode(), request.getRequestLine());
                wirePayloadIfEnabled(wire, request);
                utils.logRequest(headerLog, request, ">>");
                nativeRequest = convert(request);
                response = invoke(nativeRequest);

                logger.debug("Receiving response %s: %s", request.hashCode(), response.getStatusLine());
                utils.logResponse(headerLog, response, "<<");
                if (response.getPayload() != null && wire.enabled())
                    wire.input(response);
                nativeRequest = null; // response took ownership of streams
                int statusCode = response.getStatusCode();
                if (statusCode >= 300) {
                    if (shouldContinue(command, response))
                        continue;
                    else
                        break;
                } else {
                    break;
                }
            } catch (Exception e) {
                IOException ioe = getFirstThrowableOfType(e, IOException.class);
                if (ioe != null && shouldContinue(command, ioe)) {
                    continue;
                }
                command.setException(new HttpResponseException(e.getMessage() + " connecting to "
                        + command.getCurrentRequest().getRequestLine(), command, null, e));
                break;

            } finally {
                cleanup(nativeRequest);
            }
        }
        if (command.getException() != null)
            throw propagate(command.getException());
        return response;
    }
}
