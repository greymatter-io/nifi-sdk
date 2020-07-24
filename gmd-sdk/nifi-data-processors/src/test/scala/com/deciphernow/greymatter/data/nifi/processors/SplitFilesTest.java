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


public class SplitFilesTest {
    private TestRunner testRunner;
    private String theTestFile = "SplitFileTestFile" + Long.toString(System.nanoTime());
    private String fileContent = "FileToBeSplit";
    private Boolean isLinux = (System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).indexOf("nux") >= 0);

    private java.nio.file.Path getTestFilePath() {
        return Paths.get("/tmp/" + theTestFile);
    }

    private String getTempFilePathForSplitFiles() {
        return "/tmp/SplitFilesTest";
    }

    @Before
    public void init() {
        if(!isLinux) return;

        // Make things verbose:
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.standard", "debug");
        testRunner = TestRunners.newTestRunner(ExecuteGroovyScript.class);

        // ### Setup a test file in /tmp
        try {
            Files.write(getTestFilePath(), fileContent.getBytes());
        } catch(IOException e) {
            Assert.fail();
        }

        // ### Setup the processor
        // path is relative to where we are running tests from, identifiable via System.getProperty("user.dir")
        String groovyScript = "";
        try {
            groovyScript = loadScriptFile("../../nifi-script-processors/SplitFiles.groovy");
        } catch(IOException e) {
            e.printStackTrace();
            System.out.println(e.toString());
            Assert.fail();
        }
        // Customize for testing by using a different temp folder, and splitting on smaller file size
        groovyScript = groovyScript.replaceAll("def tf = '/home/nifi/tempfiles'", "def tf = '" + getTempFilePathForSplitFiles() + "'");
        groovyScript = groovyScript.replaceAll("def fps = '4G'", "def fps = '4'");
        // Assign configured script to processor
        testRunner.setProperty(ExecuteGroovyScript.SCRIPT_BODY, groovyScript);        
    }

    @After
    public void cleanup() throws IOException {
        if(!isLinux) return;

        // Delete the input file
        Files.delete(getTestFilePath());

        // Delete the split files
        FileUtils.deleteDirectory(new File(getTempFilePathForSplitFiles()));
    }

    private String loadScriptFile(String path) throws IOException {
        Charset encoding = StandardCharsets.UTF_8;
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    @Test
    public void testSplitFilesScript() {
        if(!isLinux) return;

        // ### Prepare an input flowfile
        final InputStream content = new ByteArrayInputStream(fileContent.getBytes()); 
        // expected attributes
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("filename", theTestFile);
        attributes.put("path", "tmp/");
        attributes.put("absolute.path", "/tmp/");
        attributes.put("file.owner", "bobbytables");
        attributes.put("file.group", "bobbytables");
        attributes.put("file.permissions", "rwxrwxr--");
        attributes.put("file.size", Long.toString(fileContent.length()));
        attributes.put("file.lastModifiedTime", "0");
        attributes.put("file.lastAccessTime", "0");
        attributes.put("file.creationTime", "0");

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
            // since there was a failure, lets dump the splitfiles.error.message
            flowFile = (MockFlowFile) failureQueue.get(0);
            assertEquals("Error message", "", flowFile.getAttribute("splitfiles.error.message"));
        }
        assertEquals("Count of flowfiles in failure relationship", 0, failureQueue.size());
        // -- make sure our success queue has expected attribute values on the flowfiles
        final List<MockFlowFile> successQueue = testRunner.getFlowFilesForRelationship(ExecuteGroovyScript.REL_SUCCESS);
        int successSize = successQueue.size();
        assertEquals("Count of flowfiles in success relationship", 5, successSize);
        verifyAttributes(successQueue, 0, "filepart_aaa", "4");
        verifyAttributes(successQueue, 1, "filepart_aab", "4");
        verifyAttributes(successQueue, 2, "filepart_aac", "4");
        verifyAttributes(successQueue, 3, "filepart_aad", "1");
        verifyAttributes(successQueue, 4, "filepart_zzz", "0");
    }

    private void verifyAttributes(List queue, int idx, String filename, String filesize) {
        FlowFile flowFile = (FlowFile) queue.get(idx);
        assertEquals("Flowfile " + Integer.toString(idx) + " filename", filename, flowFile.getAttribute("filename"));
        assertEquals("Flowfile " + Integer.toString(idx) + " file.size", filesize, flowFile.getAttribute("file.size"));
    }
}


