package com.deciphernow.greymatter.data.nifi.processors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.ssl.StandardSSLContextService;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class GetPoliciesTest {
    private TestRunner testRunner;
    private Boolean enabled;

    @Before
    public void init() {
        // Make things verbose:
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.standard", "debug");
        testRunner = TestRunners.newTestRunner(GetPolicies.class);
        enabled = false;
    }

    public StandardSSLContextService MakeSSL(){
        final Map<String, String> sslProperties = new HashMap<>();
        sslProperties.put(StandardSSLContextService.KEYSTORE.getName(), "../certs/nifinpe.jks");
        sslProperties.put(StandardSSLContextService.KEYSTORE_PASSWORD.getName(), "bmlmaXVzZXIK");
        sslProperties.put(StandardSSLContextService.KEYSTORE_TYPE.getName(), "JKS");
        sslProperties.put(StandardSSLContextService.TRUSTSTORE.getName(), "../certs/rootCA.jks");
        sslProperties.put(StandardSSLContextService.TRUSTSTORE_PASSWORD.getName(), "devotion");
        sslProperties.put(StandardSSLContextService.TRUSTSTORE_TYPE.getName(), "JKS");
        final StandardSSLContextService sslService = new StandardSSLContextService();
        // This will fail if not given the above things and will not run tests unless that exception is handled
        try{
            testRunner.addControllerService("ssl-context", sslService, sslProperties);
        } catch (InitializationException e){
            System.out.println("Initialization issues");
        }

        return sslService;
    }

    // Test the happy path
    @Test
    public void testPoliciesProcessor() {
        if(!enabled) return;
        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        String user = "dborncamp";
        String permissionStr = "{\"read\":{\"allow\":[\""+user+"\"]},\"create\":{\"allow\":[\""+user+"\"]},\"update\":{\"allow\":[]},\"delete\":{\"allow\":[]}}";
        String acm = "{\"version\": \"2.1.0\" ,\"classif\": \"U\"}";

        mapped.put("permission", permissionStr);
        mapped.put("acm", acm);

        StandardSSLContextService sslService = MakeSSL();
        testRunner.enableControllerService(sslService);
        testRunner.setProperty(GetPolicies.PROP_SSL_CONTEXT_SERVICE, "ssl-context");

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(GetPolicies.PROP_BASE_URL, "https://127.0.0.1:8081/");

        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(GetPolicies.REL_RESPONSE);

        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should only be 1 match", 1, result.size());

        assertEquals("200", flowFile.getAttribute("getpolicies.status.code"));
        assertEquals("OK", flowFile.getAttribute("getpolicies.status.message"));
        assertEquals("", flowFile.getAttribute("getpolicies.response.body"));  // it is 200, no errors should be here
        assertEquals("https://127.0.0.1:8081/convert/addpermissions", flowFile.getAttribute("getpolicies.request.url"));
        assertEquals("CN=gm-data,OU=Engineering,O=Untrusted Example,L=Baltimore,ST=MD,C=US", flowFile.getAttribute("getpolicies.remote.dn"));
        assertEquals("{\"background\":\"007A33\",\"label\":\"UNCLASSIFIED\",\"foreground\":\"#FFFFFF\"}", flowFile.getAttribute("gmdata.security"));
        assertEquals("(if (and (or (contains dissem_countries USA)) (and (contains f_clearance u))) (if (tells user_dn) (or (if (or (contains f_share dborncamp)) (yield C R))) (yield R X)))", flowFile.getAttribute("gmdata.lisp"));

        assertEquals("\"{\\\"acm\\\":{\\\"classif\\\":\\\"U\\\",\\\"f_clearance\\\":[\\\"u\\\"],\\\"portion\\\":\\\"U\\\",\\\"banner\\\":\\\"UNCLASSIFIED\\\",\\\"share\\\":{},\\\"version\\\":\\\"2.1.0\\\",\\\"dissem_countries\\\":[\\\"USA\\\"]},\\\"permission\\\":{\\\"read\\\":{\\\"allow\\\":[\\\"_everyone\\\"]},\\\"create\\\":{\\\"allow\\\":[\\\"dborncamp\\\"]},\\\"update\\\":{},\\\"purge\\\":{},\\\"delete\\\":{},\\\"execute\\\":{}}}\"", flowFile.getAttribute("gmdata.originalobjectpolicy"));


        // make sure that the OP is actually JSON
        JsonObject returnedOP = new JsonParser().parse(flowFile.getAttribute("gmdata.objectpolicy")).getAsJsonObject();
        String expectedOP = "{\"requirements\":{\"a\":[{\"a\":[{\"a\":[{\"a\":[{\"v\":\"dissem_countries\"},{\"v\":\"USA\"}],\"f\":\"contains\"}],\"f\":\"or\"},{\"a\":[{\"a\":[{\"v\":\"f_clearance\"},{\"v\":\"u\"}],\"f\":\"contains\"}],\"f\":\"and\"}],\"f\":\"and\"},{\"a\":[{\"a\":[{\"v\":\"user_dn\"}],\"f\":\"tells\"},{\"a\":[{\"a\":[{\"a\":[{\"a\":[{\"v\":\"f_share\"},{\"v\":\""+user+"\"}],\"f\":\"contains\"}],\"f\":\"or\"},{\"a\":[{\"v\":\"C\"},{\"v\":\"R\"}],\"f\":\"yield\"}],\"f\":\"if\"}],\"f\":\"or\"},{\"a\":[{\"v\":\"R\"},{\"v\":\"X\"}],\"f\":\"yield\"}],\"f\":\"if\"}],\"f\":\"if\"},\"label\":\"ACM-DATA-POLICY-GENERATED\"}";
        System.out.println("Object Policy: " + returnedOP);

        assertEquals("Not the OP we expected", expectedOP, returnedOP.toString());

    }

    // Test the happy path
    @Test
    public void testPoliciesProcessorWithoutSlash() {
        if(!enabled) return;
        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        String user = "dborncamp";
        String permissionStr = "{\"read\":{\"allow\":[\""+user+"\"]},\"create\":{\"allow\":[]},\"update\":{\"allow\":[]},\"delete\":{\"allow\":[]}}";
        String acm = "{\"version\": \"2.1.0\" ,\"classif\": \"U\"}";

        mapped.put("permission", permissionStr);
        mapped.put("acm", acm);

        StandardSSLContextService sslService = MakeSSL();
        testRunner.enableControllerService(sslService);
        testRunner.setProperty(GetPolicies.PROP_SSL_CONTEXT_SERVICE, "ssl-context");

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(GetPolicies.PROP_BASE_URL, "https://127.0.0.1:8081");

        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(GetPolicies.REL_RESPONSE);

        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should only be 1 match", 1, result.size());

        assertEquals("200", flowFile.getAttribute("getpolicies.status.code"));
        assertEquals("https://127.0.0.1:8081/convert/addpermissions", flowFile.getAttribute("getpolicies.request.url"));

        // make sure that the OP is actually JSON
        JsonObject jsonObject = new JsonParser().parse(flowFile.getAttribute("gmdata.objectpolicy")).getAsJsonObject();
        System.out.println("Object Policy: " + jsonObject);

    }

    // A happy path failure because the endpoint was wrong
    @Test
    public void testPoliciesProcessorFailure() {
        if(!enabled) return;
        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        String user = "dborncamp";
        String permissionStr = "{\"read\":{\"allow\":[\""+user+"\"]},\"create\":{\"allow\":[]},\"update\":{\"allow\":[]},\"delete\":{\"allow\":[]}}";
        String acm = "{\"version\": \"2.1.0\" ,\"classif\": \"U\"}";

        mapped.put("permission", permissionStr);
        mapped.put("acm", acm);

        StandardSSLContextService sslService = MakeSSL();
        testRunner.enableControllerService(sslService);
        testRunner.setProperty(GetPolicies.PROP_SSL_CONTEXT_SERVICE, "ssl-context");

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(GetPolicies.PROP_BASE_URL, "https://127.0.0.1:8081/convert/wrong");

        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(GetPolicies.REL_FAILURE);

        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should only be 1 match", 1, result.size());

        assertEquals("404", flowFile.getAttribute("getpolicies.status.code"));
        assertEquals("https://127.0.0.1:8081/convert/wrong/convert/addpermissions", flowFile.getAttribute("getpolicies.request.url"));

    }


}


