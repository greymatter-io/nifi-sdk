/*
Adapted from https://github.com/apache/nifi/blob/rel/nifi-1.10.0/nifi-nar-bundles/nifi-standard-bundle/nifi-standard-processors/src/main/java/org/apache/nifi/processors/standard/InvokeHTTP.java
 */
package com.deciphernow.greymatter.data.nifi.processors;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.OutputStream;

import java.security.Principal;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.AttributeExpression;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.ssl.SSLContextService;
import org.apache.nifi.ssl.SSLContextService.ClientAuth;
import org.apache.nifi.stream.io.StreamUtils;
import org.json.JSONObject;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.impl.EnglishReasonPhraseCatalog;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

@Tags({"gmdata"})
@InputRequirement(Requirement.INPUT_ALLOWED)
@CapabilityDescription("This custom processor will facilitate transforming a provided permission structure and access control model into the objectPolicy and originalObjectPolicy for assignment into a Grey Matter Data event.")
@WritesAttributes({
        @WritesAttribute(attribute = "getpolicies.status.code", description = "The status code that is returned"),
        @WritesAttribute(attribute = "getpolicies.status.message", description = "The status message that is returned"),
        @WritesAttribute(attribute = "getpolicies.response.body", description = "In the instance where the status code received is not a success (2xx) "
                + "then the response body will be put to the 'getpolicies.response.body' attribute of the request FlowFile."),
        @WritesAttribute(attribute = "getpolicies.request.url", description = "The request URL"),
        @WritesAttribute(attribute = "getpolicies.tx.id", description = "The transaction ID that is returned after reading the response"),
        @WritesAttribute(attribute = "getpolicies.remote.dn", description = "The DN of the remote server"),
        @WritesAttribute(attribute = "getpolicies.java.exception.class", description = "The Java exception class raised when the processor fails"),
        @WritesAttribute(attribute = "getpolicies.java.exception.message", description = "The Java exception message raised when the processor fails"),
        @WritesAttribute(attribute = "user-defined", description = "If the 'Put Response Body In Attribute' property is set then whatever it is set to "
                + "will become the attribute key and the value would be the body of the HTTP response."),
        @WritesAttribute(attribute = "gmdata.objectpolicy", description = "A fully populated object policy suitable to populate in a Grey Matter Data event for access control."),
        @WritesAttribute(attribute = "gmdata.originalobjectpolicy", description = "A compound structure containing the inputs used to produce the object policy."),
        @WritesAttribute(attribute = "gmdata.security", description = "The security banner information for the Object Policy."),
        @WritesAttribute(attribute = "gmdata.lisp", description = "Object Policy lisp conversion information"),
})
@DynamicProperty(name = "Header Name", value = "Attribute Expression Language", expressionLanguageScope = ExpressionLanguageScope.FLOWFILE_ATTRIBUTES,
        description = "Send request header with a key matching the Dynamic Property Key and a value created by evaluating "
                + "the Attribute Expression Language set in the value of the Dynamic Property.")
public final class GetPolicies extends AbstractProcessor {
    // flowfile attribute keys returned after reading the response
    public final static String STATUS_CODE = "getpolicies.status.code";
    public final static String STATUS_MESSAGE = "getpolicies.status.message";
    public final static String RESPONSE_BODY = "getpolicies.response.body";
    public final static String REQUEST_URL = "getpolicies.request.url";
    public final static String TRANSACTION_ID = "getpolicies.tx.id";
    public final static String REMOTE_DN = "getpolicies.remote.dn";
    public final static String EXCEPTION_CLASS = "getpolicies.java.exception.class";
    public final static String EXCEPTION_MESSAGE = "getpolicies.java.exception.message";
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    // Set of HTTP header names explicitly excluded from requests.
    private static final Map<String, String> excludedHeaders = new HashMap<String, String>();

    // properties
    public static final PropertyDescriptor PROP_BASE_URL = new PropertyDescriptor.Builder()
            .name("Remote base URL")
            .description("Remote URL which will be connected to, including scheme, host, port, path.")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.URL_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROP_CONNECT_TIMEOUT = new PropertyDescriptor.Builder()
            .name("Connection Timeout")
            .description("Max wait time for connection to remote service.")
            .required(true)
            .defaultValue("5 secs")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROP_READ_TIMEOUT = new PropertyDescriptor.Builder()
            .name("Read Timeout")
            .description("Max wait time for response from remote service.")
            .required(true)
            .defaultValue("15 secs")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROP_FOLLOW_REDIRECTS = new PropertyDescriptor.Builder()
            .name("Follow Redirects")
            .description("Follow HTTP redirects issued by remote server.")
            .required(true)
            .defaultValue("True")
            .allowableValues("True", "False")
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROP_SSL_CONTEXT_SERVICE = new PropertyDescriptor.Builder()
            .name("SSL Context Service")
            .description("The SSL Context Service used to provide client certificate information for TLS/SSL (https) connections.")
            .required(false)
            .identifiesControllerService(SSLContextService.class)
            .build();

    public static final PropertyDescriptor PROP_PUT_OUTPUT_IN_ATTRIBUTE = new PropertyDescriptor.Builder()
            .name("Put Response Body In Attribute")
            .description("If set, the response body received back will be put into an attribute of the original FlowFile instead of a separate "
                    + "FlowFile. The attribute key to put to is determined by evaluating value of this property. ")
            .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING))
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();

    public static final PropertyDescriptor PROP_PUT_ATTRIBUTE_MAX_LENGTH = new PropertyDescriptor.Builder()
            .name("Max Length To Put In Attribute")
            .description("If routing the response body to an attribute of the original (by setting the \"Put response body in attribute\" "
                    + "property or by receiving an error status code), the number of characters put to the attribute value will be at "
                    + "most this amount. This is important because attributes are held in memory and large attributes will quickly "
                    + "cause out of memory issues. If the output goes longer than this value, it will be truncated to fit. "
                    + "Consider making this smaller if able.")
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .defaultValue("256")
            .build();

    public static final PropertyDescriptor PROP_ADD_HEADERS_TO_REQUEST = new PropertyDescriptor.Builder()
            .name("Add Response Headers to Request")
            .description("Enabling this property saves all the response headers to the original request. This may be when the response headers are needed "
                    + "but a response is not generated due to the status code received.")
            .required(false)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();

    public static final List<PropertyDescriptor> PROPERTIES = Collections.unmodifiableList(Arrays.asList(
            PROP_BASE_URL,
            PROP_SSL_CONTEXT_SERVICE,
            PROP_CONNECT_TIMEOUT,
            PROP_READ_TIMEOUT,
            PROP_FOLLOW_REDIRECTS,
            PROP_PUT_OUTPUT_IN_ATTRIBUTE,
            PROP_PUT_ATTRIBUTE_MAX_LENGTH,
            PROP_ADD_HEADERS_TO_REQUEST
    ));

    // relationships
    public static final Relationship REL_RESPONSE = new Relationship.Builder()
            .name("Response")
            .description("A Response FlowFile will be routed upon success (2xx status codes).")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("Failure")
            .description("The original FlowFile will be routed on any type of connection failure, timeout or general exception. "
                    + "It will have new attributes detailing the request.")
            .build();

    public static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            REL_RESPONSE, REL_FAILURE)));

    private volatile Set<String> dynamicPropertyNames = new HashSet<>();

    private final AtomicReference<OkHttpClient> okHttpClientAtomicReference = new AtomicReference<>();

    protected void init(ProcessorInitializationContext context) {
        excludedHeaders.put("Trusted Hostname", "HTTP request header '{}' excluded. " +
                "Update processor to use the SSLContextService instead. " +
                "See the Access Policies section in the System Administrator's Guide.");
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return PROPERTIES;
    }

    @Override
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(String propertyDescriptorName) {
        return new PropertyDescriptor.Builder()
                .required(false)
                .name(propertyDescriptorName)
                .addValidator(StandardValidators.createAttributeExpressionLanguageValidator(AttributeExpression.ResultType.STRING, true))
                .dynamic(true)
                .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
                .build();
    }

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    @Override
    public void onPropertyModified(final PropertyDescriptor descriptor, final String oldValue, final String newValue) {
        if (descriptor.isDynamic()) {
            final Set<String> newDynamicPropertyNames = new HashSet<>(dynamicPropertyNames);
            if (newValue == null) {
                newDynamicPropertyNames.remove(descriptor.getName());
            } else if (oldValue == null) {    // new property
                newDynamicPropertyNames.add(descriptor.getName());
            }
            this.dynamicPropertyNames = Collections.unmodifiableSet(newDynamicPropertyNames);
        }
    }

    @OnScheduled
    public void setUpClient(final ProcessContext context) throws IOException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        final ComponentLog logger = getLogger();
        logger.debug("Setup");
        okHttpClientAtomicReference.set(null);

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder();

        // Set timeouts
        okHttpClientBuilder.connectTimeout((context.getProperty(PROP_CONNECT_TIMEOUT).asTimePeriod(TimeUnit.MILLISECONDS).intValue()), TimeUnit.MILLISECONDS);
        okHttpClientBuilder.readTimeout(context.getProperty(PROP_READ_TIMEOUT).asTimePeriod(TimeUnit.MILLISECONDS).intValue(), TimeUnit.MILLISECONDS);

        // Set whether to follow redirects
        okHttpClientBuilder.followRedirects(context.getProperty(PROP_FOLLOW_REDIRECTS).asBoolean());

        final SSLContextService sslService = context.getProperty(PROP_SSL_CONTEXT_SERVICE).asControllerService(SSLContextService.class);
        final SSLContext sslContext = sslService == null ? null : sslService.createSSLContext(ClientAuth.NONE);

        // check if the ssl context is set and add the factory if so
        if (sslContext != null) {
            setSslSocketFactory(okHttpClientBuilder, sslService, sslContext);
        }

        okHttpClientAtomicReference.set(okHttpClientBuilder.build());
        logger.debug("Finished setup");
    }

    /*
        Overall, this method is based off of examples from OkHttp3 documentation:
            https://square.github.io/okhttp/3.x/okhttp/okhttp3/OkHttpClient.Builder.html#sslSocketFactory-javax.net.ssl.SSLSocketFactory-javax.net.ssl.X509TrustManager-
            https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/CustomTrust.java#L156

        In-depth documentation on Java Secure Socket Extension (JSSE) Classes and interfaces:
            https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#JSSEClasses
     */
    private void setSslSocketFactory(OkHttpClient.Builder okHttpClientBuilder, SSLContextService sslService, SSLContext sslContext)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {

        final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
        // initialize the KeyManager array to null and we will overwrite later if a keystore is loaded
        KeyManager[] keyManagers = null;

        // we will only initialize the keystore if properties have been supplied by the SSLContextService
        if (sslService.isKeyStoreConfigured()) {
            final String keystoreLocation = sslService.getKeyStoreFile();
            final String keystorePass = sslService.getKeyStorePassword();
            final String keystoreType = sslService.getKeyStoreType();

            // prepare the keystore
            final KeyStore keyStore = KeyStore.getInstance(keystoreType);

            try (FileInputStream keyStoreStream = new FileInputStream(keystoreLocation)) {
                keyStore.load(keyStoreStream, keystorePass.toCharArray());
            }

            keyManagerFactory.init(keyStore, keystorePass.toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        }

        // we will only initialize the truststure if properties have been supplied by the SSLContextService
        if (sslService.isTrustStoreConfigured()) {
            // load truststore
            final String truststoreLocation = sslService.getTrustStoreFile();
            final String truststorePass = sslService.getTrustStorePassword();
            final String truststoreType = sslService.getTrustStoreType();

            KeyStore truststore = KeyStore.getInstance(truststoreType);
            truststore.load(new FileInputStream(truststoreLocation), truststorePass.toCharArray());
            trustManagerFactory.init(truststore);
        }

         /*
            TrustManagerFactory.getTrustManagers returns a trust manager for each type of trust material. Since we are getting a trust manager factory that uses "X509"
            as it's trust management algorithm, we are able to grab the first (and thus the most preferred) and use it as our x509 Trust Manager

            https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/TrustManagerFactory.html#getTrustManagers--
         */
        final X509TrustManager x509TrustManager;
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers[0] != null) {
            x509TrustManager = (X509TrustManager) trustManagers[0];
        } else {
            throw new IllegalStateException("List of trust managers is null");
        }

        // if keystore properties were not supplied, the keyManagers array will be null
        sslContext.init(keyManagers, trustManagerFactory.getTrustManagers(), null);

        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        okHttpClientBuilder.sslSocketFactory(sslSocketFactory, x509TrustManager);
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        final ComponentLog logger = getLogger();
        logger.debug("Triggering");
        OkHttpClient okHttpClient = okHttpClientAtomicReference.get();

        FlowFile requestFlowFile = session.get();

        if (requestFlowFile == null) {
            logger.debug("The flowfile was empty");
            return;
        }

        // Setting some initial variables
        final int maxAttributeSize = context.getProperty(PROP_PUT_ATTRIBUTE_MAX_LENGTH).asInteger();

        // Every request/response cycle has a unique transaction id which will be stored as a flowfile attribute.
        final UUID txId = UUID.randomUUID();

        try {
            // read the url property from the context
            final String urlstr = trimToEmpty(context.getProperty(PROP_BASE_URL).evaluateAttributeExpressions(requestFlowFile).getValue());
            URL url;
            if (urlstr.endsWith("/")) {
                url = new URL(urlstr + "convert/addpermissions");
            } else {
                url = new URL(urlstr + "/convert/addpermissions");
            }

            Request httpRequest = configureRequest(context, requestFlowFile, url);

            // log request
            logRequest(logger, httpRequest);

            // emit send provenance event if successfully sent to the server
            if (httpRequest.body() != null) {
                session.getProvenanceReporter().send(requestFlowFile, url.toExternalForm(), true);
            }

            final long startNanos = System.nanoTime();

            try (Response responseHttp = okHttpClient.newCall(httpRequest).execute()) {
                // output the raw response headers (DEBUG level only)
                logResponse(logger, url, responseHttp);

                // store the status code and message
                int statusCode = responseHttp.code();
                String statusMessage = responseHttp.message();

                if (statusCode == 0) {
                    throw new IllegalStateException("Status code unknown, connection hasn't been attempted.");
                }
                if (statusMessage.length() == 0) {
                    statusMessage = org.apache.http.impl.EnglishReasonPhraseCatalog.INSTANCE.getReason(statusCode,null);
                }

                // Create a map of the status attributes that are always written to the request and response FlowFiles
                Map<String, String> statusAttributes = new HashMap<>();
                statusAttributes.put(STATUS_CODE, String.valueOf(statusCode));
                statusAttributes.put(STATUS_MESSAGE, statusMessage);
                statusAttributes.put(REQUEST_URL, url.toExternalForm());
                statusAttributes.put(TRANSACTION_ID, txId.toString());

                requestFlowFile = session.putAllAttributes(requestFlowFile, statusAttributes);

                // write the response headers as attributes regardless of response
                // this will overwrite any existing flowfile attributes
                requestFlowFile = session.putAllAttributes(requestFlowFile, convertAttributesFromHeaders(responseHttp));

                if (!isSuccess(statusCode)) {
                    route(requestFlowFile, session, statusCode);

                    return;
                }

                ResponseBody responseBody = responseHttp.body();
                boolean bodyExists = responseBody != null;

                InputStream responseBodyStream = null;
                SoftLimitBoundedByteArrayOutputStream outputStreamToRequestAttribute = null;
                try {
                    responseBodyStream = bodyExists ? responseBody.byteStream() : null;
                    if (responseBodyStream != null) {
                        outputStreamToRequestAttribute = new SoftLimitBoundedByteArrayOutputStream(maxAttributeSize);
                    }

                    // add all of the status attributes
                    requestFlowFile = session.putAllAttributes(requestFlowFile, statusAttributes);

                    // write the response headers as attributes
                    // this will overwrite any existing flowfile attributes
                    requestFlowFile = session.putAllAttributes(requestFlowFile, convertAttributesFromHeaders(responseHttp));

                    // transfer the message body to the payload
                    // can potentially be null in edge cases
                    if (bodyExists) {
                        // write content type attribute to response flowfile if it is available
                        if (responseBody.contentType() != null) {
                            logger.debug("content type not null");
                            requestFlowFile = session.putAttribute(requestFlowFile, CoreAttributes.MIME_TYPE.key(), responseBody.contentType().toString());

                            String responseString = responseBody.string();
                            logger.debug("Body: " + responseString);
                            JSONObject responseJson = new JSONObject(responseString);
                            logger.debug("response JSON from Data Policies server: " + responseJson);

                            session.putAttribute(requestFlowFile, "gmdata.objectpolicy", responseJson.getJSONObject("objectpolicy").toString());
                            session.putAttribute(requestFlowFile, "gmdata.lisp", responseJson.getString("lisp"));
                            session.putAttribute(requestFlowFile, "gmdata.security", responseJson.getJSONObject("security").toString());
                            session.putAttribute(requestFlowFile, "gmdata.originalobjectpolicy", quote(responseJson.getJSONObject("originalobjectpolicy").toString()));

                            logger.debug("set attributes");
                        }

                        // emit provenance event
                        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                        session.getProvenanceReporter().fetch(requestFlowFile, url.toExternalForm(), millis);

                        String attributeKey = context.getProperty(PROP_PUT_OUTPUT_IN_ATTRIBUTE).evaluateAttributeExpressions(requestFlowFile).getValue();
                        if (attributeKey == null) {
                            attributeKey = RESPONSE_BODY;
                        }
                        byte[] outputBuffer;
                        int size;

                        if (outputStreamToRequestAttribute != null) {
                            outputBuffer = outputStreamToRequestAttribute.getBuffer();
                            size = outputStreamToRequestAttribute.size();
                        } else {
                            outputBuffer = new byte[maxAttributeSize];
                            size = StreamUtils.fillBuffer(responseBodyStream, outputBuffer, false);
                        }
                        String bodyString = new String(outputBuffer, 0, size, getCharsetFromMediaType(responseBody.contentType()));
                        requestFlowFile = session.putAttribute(requestFlowFile, attributeKey, bodyString);

                        millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                        session.getProvenanceReporter().modifyAttributes(requestFlowFile, "The " + attributeKey + " has been added. The value of which is the body of a http call to "
                                + url.toExternalForm() + ". It took " + millis + "millis,");
                    }
                    // make sure to close all of the streams
                } finally {
                    if(outputStreamToRequestAttribute != null){
                        outputStreamToRequestAttribute.close();
                    }
                    if(responseBodyStream != null){
                        responseBodyStream.close();
                    }
                }

                route(requestFlowFile, session, statusCode);

            }
        } catch (final Exception e) {
            // penalize or yield
            if (requestFlowFile != null) {
                logger.error("Routing to {} due to exception: {}", new Object[]{REL_FAILURE.getName(), e}, e.fillInStackTrace());
                logger.error("Trace:");
                e.printStackTrace();
                requestFlowFile = session.penalize(requestFlowFile);
                requestFlowFile = session.putAttribute(requestFlowFile, EXCEPTION_CLASS, e.getClass().getName());
                requestFlowFile = session.putAttribute(requestFlowFile, EXCEPTION_MESSAGE, e.getMessage());
                // transfer original to failure
                session.transfer(requestFlowFile, REL_FAILURE);
            } else {
                logger.error("Yielding processor due to exception encountered as a source processor: {}", e);
                context.yield();
            }
        }
    }

    // taken from https://stackoverflow.com/a/16652683/4508233
    public static String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char         c = 0;
        int          i;
        int          len = string.length();
        StringBuilder sb = new StringBuilder(len + 4);
        String       t;

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    // if (b == '<') {
                    sb.append('\\');
                    //}
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }


    private Request configureRequest(final ProcessContext context, final FlowFile requestFlowFile, URL url) {
        final ComponentLog logger = getLogger();
        Request.Builder requestBuilder = new Request.Builder();
        logger.debug("configuring request");

        requestBuilder = requestBuilder.url(url);

        JSONObject requestJson = new JSONObject();
        requestJson.put("acm", new JSONObject(requestFlowFile.getAttribute("acm")));
        requestJson.put("permission", new JSONObject(requestFlowFile.getAttribute("permission")));
        logger.debug("Got the permission");

        MediaType media = MediaType.parse(DEFAULT_CONTENT_TYPE);
        logger.debug("Submitting to Data Policy server - Content type: "+ media + "  The json to submit: "+requestJson.toString());

        RequestBody requestBody = null;
        try {
            requestBody = RequestBody.create(media, requestJson.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        requestBuilder = requestBuilder.post(requestBody);

        requestBuilder = setHeaderProperties(context, requestBuilder, requestFlowFile);

        return requestBuilder.build();
    }


    private Request.Builder setHeaderProperties(final ProcessContext context, Request.Builder requestBuilder, final FlowFile requestFlowFile) {

        final ComponentLog logger = getLogger();
        for (String headerKey : dynamicPropertyNames) {
            String headerValue = context.getProperty(headerKey).evaluateAttributeExpressions(requestFlowFile).getValue();

            // don't include any of the excluded headers, log instead
            if (excludedHeaders.containsKey(headerKey)) {
                logger.info(excludedHeaders.get(headerKey), new Object[]{headerKey});
                continue;
            }
            requestBuilder = requestBuilder.addHeader(headerKey, headerValue);
        }

        return requestBuilder;
    }

    private void route(FlowFile response, ProcessSession session, int statusCode){
        if (isSuccess(statusCode)) {
            session.transfer(response, REL_RESPONSE);
        } else {
            session.transfer(response, REL_FAILURE);
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode / 100 == 2;
    }

    private void logRequest(ComponentLog logger, Request request) {
        logger.debug("\nRequest to remote service:\n\t{}\n{}",
                new Object[]{request.url().url().toExternalForm(), getLogString(request.headers().toMultimap())});
    }

    private void logResponse(ComponentLog logger, URL url, Response response) {
        logger.debug("\nResponse from remote service:\n\t{}\n{}",
                new Object[]{url.toExternalForm(), getLogString(response.headers().toMultimap())});
    }

    private String getLogString(Map<String, List<String>> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            List<String> list = entry.getValue();
            if (list.isEmpty()) {
                continue;
            }
            sb.append("\t");
            sb.append(entry.getKey());
            sb.append(": ");
            if (list.size() == 1) {
                sb.append(list.get(0));
            } else {
                sb.append(list.toString());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Convert a collection of string values into a overly simple comma separated string.
     * <p/>
     * Does not handle the case where the value contains the delimiter. i.e. if a value contains a comma, this method does nothing to try and escape or quote the value, in traditional csv style.
     */
    private String csv(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }

        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            value = value.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
        }
        return sb.toString().trim();
    }

    /**
     * Returns a Map of flowfile attributes from the response http headers. Multivalue headers are naively converted to comma separated strings.
     */
    private Map<String, String> convertAttributesFromHeaders(Response responseHttp){
        // create a new hashmap to store the values from the connection
        Map<String, String> map = new HashMap<>();
        responseHttp.headers().names().forEach( (key) -> {
            if (key == null) {
                return;
            }

            List<String> values = responseHttp.headers().values(key);

            // we ignore any headers with no actual values (rare)
            if (values == null || values.isEmpty()) {
                return;
            }

            // create a comma separated string from the values, this is stored in the map
            String value = csv(values);

            // put the csv into the map
            map.put(key, value);
        });

        if (responseHttp.request().isHttps()) {
            Principal principal = responseHttp.handshake().peerPrincipal();

            if (principal != null) {
                map.put(REMOTE_DN, principal.getName());
            }
        }
        return map;
    }

    private Charset getCharsetFromMediaType(MediaType contentType) {
        return contentType != null ? contentType.charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
    }
}

// Lifted from: https://github.com/apache/nifi/blob/rel/nifi-1.10.0/nifi-nar-bundles/nifi-standard-bundle/nifi-standard-processors/src/main/java/org/apache/nifi/processors/standard/util/SoftLimitBoundedByteArrayOutputStream.java
class SoftLimitBoundedByteArrayOutputStream extends OutputStream {
    /*
     * This Bounded Array Output Stream (BAOS) allows the user to write to the output stream up to a specified limit.
     * Higher than that limit the BAOS will silently return and not put more into the buffer. It also will not throw an error.
     * This effectively truncates the stream for the user to fit into a bounded array.
     */

    private final byte[] buffer;
    private int limit;
    private int count;

    public SoftLimitBoundedByteArrayOutputStream(int capacity) {
        this(capacity, capacity);
    }

    public SoftLimitBoundedByteArrayOutputStream(int capacity, int limit) {
        if ((capacity < limit) || (capacity | limit) < 0) {
            throw new IllegalArgumentException("Invalid capacity/limit");
        }
        this.buffer = new byte[capacity];
        this.limit = limit;
        this.count = 0;
    }

    @Override
    public void write(int b) throws IOException {
        if (count >= limit) {
            return;
        }
        buffer[count++] = (byte) b;
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length)
                || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        if (count + len > limit) {
            len = limit-count;
            if(len == 0){
                return;
            }
        }

        System.arraycopy(b, off, buffer, count, len);
        count += len;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int size() {
        return count;
    }
}