package com.deciphernow.greymatter.data.nifi.processors;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processors.groovyx.ExecuteGroovyScript;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class S3RequestSplitTest {
    private TestRunner testRunner;

    @Before
    public void init() {
        // Make things verbose:
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.standard", "debug");
        testRunner = TestRunners.newTestRunner(ExecuteGroovyScript.class);

        // ### Setup the processor
        // path is relative to where we are running tests from, identifiable via System.getProperty("user.dir")
        String groovyScript = "";
        try {
            groovyScript = loadScriptFile("../../nifi-script-processors/S3RequestSplit.groovy");
        } catch(IOException e) {
            e.printStackTrace();
            System.out.println(e.toString());
            Assert.fail();
        }
        // Customize for testing by on smaller file size
        groovyScript = groovyScript.replaceAll("def fps = '4G'", "def fps = '4B'");
        // Assign configured script to processor
        testRunner.setProperty(ExecuteGroovyScript.SCRIPT_BODY, groovyScript);        
    }

    private String loadScriptFile(String path) throws IOException {
        Charset encoding = StandardCharsets.UTF_8;
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    private void verifyAttributes(List queue, int idx, String splitSuffix, String rangeStart, String rangeLength) {
        FlowFile flowFile = (FlowFile) queue.get(idx);
        assertEquals("Flowfile " + Integer.toString(idx) + " split.suffix", splitSuffix, flowFile.getAttribute("split.suffix"));
        assertEquals("Flowfile " + Integer.toString(idx) + " s3-object-range-start", rangeStart, flowFile.getAttribute("s3-object-range-start"));
        assertEquals("Flowfile " + Integer.toString(idx) + " s3-object-range-length", rangeLength, flowFile.getAttribute("s3-object-range-length"));
    }

    @Test
    public void testS3RequestSplitLargeFile() {

        // ### Prepare an input flowfile
        final InputStream content = new ByteArrayInputStream("".getBytes());
        // inflow attributes
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("s3.bucket", "some-bucket");
        attributes.put("filename", "some-filename");
        attributes.put("s3.etag", "c659748c87bdcdd90d17cfc26bdc3013-6016");
        attributes.put("s3.isLatest", "true");
        attributes.put("s3.LastModified", "1602224826000");
        attributes.put("s3.length", "13");
        attributes.put("s3.version", "1");

        // ### Start tester
        testRunner.enqueue(content, attributes);
        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success and failure
        FlowFile flowFile;
        // -- make sure no failures
        final List failureQueue = testRunner.getFlowFilesForRelationship(ExecuteGroovyScript.REL_FAILURE);
        if(failureQueue.size() > 0) 
        {
            // since there was a failure, lets dump the error message
            flowFile = (MockFlowFile) failureQueue.get(0);
            assertEquals("Error message", "", flowFile.getAttribute("s3requestsplit.error.message"));
        }
        assertEquals("Count of flowfiles in failure relationship", 0, failureQueue.size());
        // -- make sure our success queue has expected attribute values on the flowfiles
        final List<MockFlowFile> successQueue = testRunner.getFlowFilesForRelationship(ExecuteGroovyScript.REL_SUCCESS);
        int successSize = successQueue.size();
        assertEquals("Count of flowfiles in success relationship", 5, successSize);
        verifyAttributes(successQueue, 0, "filepart_aaa", "0B", "4B");
        verifyAttributes(successQueue, 1, "filepart_aab", "4B", "4B");
        verifyAttributes(successQueue, 2, "filepart_aac", "8B", "4B");
        verifyAttributes(successQueue, 3, "filepart_aad", "12B", "1B");
        verifyAttributes(successQueue, 4, "filepart_zzz", "-1B", "-1B");
    }

    @Test
    public void testS3RequestSplitNotNeeded() {

        // ### Prepare an input flowfile
        final InputStream content = new ByteArrayInputStream("".getBytes());
        // inflow attributes
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("s3.bucket", "some-bucket");
        attributes.put("filename", "some-filename");
        attributes.put("s3.etag", "c659748c87bdcdd90d17cfc26bdc3013-6016");
        attributes.put("s3.isLatest", "true");
        attributes.put("s3.LastModified", "1602224826000");
        attributes.put("s3.length", "3");
        attributes.put("s3.version", "1");

        // ### Start tester
        testRunner.enqueue(content, attributes);
        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success and failure
        FlowFile flowFile;
        // -- make sure no failures
        final List failureQueue = testRunner.getFlowFilesForRelationship(ExecuteGroovyScript.REL_FAILURE);
        if(failureQueue.size() > 0) 
        {
            // since there was a failure, lets dump the error message
            flowFile = (MockFlowFile) failureQueue.get(0);
            assertEquals("Error message", "", flowFile.getAttribute("s3requestsplit.error.message"));
        }
        assertEquals("Count of flowfiles in failure relationship", 0, failureQueue.size());
        // -- make sure our success queue has expected attribute values on the flowfiles
        final List<MockFlowFile> successQueue = testRunner.getFlowFilesForRelationship(ExecuteGroovyScript.REL_SUCCESS);
        int successSize = successQueue.size();
        assertEquals("Count of flowfiles in success relationship", 1, successSize);
        verifyAttributes(successQueue, 0, null, "0B", "3B");
    }

}


