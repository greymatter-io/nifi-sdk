package com.deciphernow.greymatter.data.nifi.processors;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processors.groovyx.ExecuteGroovyScript;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RemoveSplitFilesTest {
    private TestRunner testRunner;

    private String getTempFilePathForRemoveSplitFiles() {
        return "/tmp/RemoveSplitFilesTest";
    }
    private Map<String, String> makeAttributes(String filename, String path, String absolutePath, String splitPart) {
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("filename", filename);
        attributes.put("path", path);
        attributes.put("absolute.path", absolutePath);
        attributes.put("split.part", splitPart);
        return attributes;
    }
    private String loadScriptFile(String path) throws IOException {
        Charset encoding = StandardCharsets.UTF_8;
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    @Before
    public void init() {

        // Make things verbose:
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.standard", "debug");
        testRunner = TestRunners.newTestRunner(ExecuteGroovyScript.class);

        // Handle to session
        ProcessSession session = testRunner.getProcessSessionFactory().createSession();

        // ### Setup some files in a subfolder in /tmp
        try {
            // Nested in a folder
            File removeSplitFilesPath = new File(getTempFilePathForRemoveSplitFiles() + "/somesubfolder/nestedsplitfile.txt");
            removeSplitFilesPath.getParentFile().mkdirs();
            Files.write(Paths.get(getTempFilePathForRemoveSplitFiles() + "/somesubfolder/nestedsplitfile.txt"), "a nested split file".getBytes(StandardCharsets.UTF_8));
            FlowFile ffNested = session.create();
            ffNested = session.putAllAttributes(ffNested, makeAttributes("nestedsplitfile.txt", "somesubfolder", getTempFilePathForRemoveSplitFiles() + "/somesubfolder", "1"));
            testRunner.enqueue(ffNested);
            // Nested sibling
            Files.write(Paths.get(getTempFilePathForRemoveSplitFiles() + "/somesubfolder/nestedsibling.txt"), "a nested split file sibling".getBytes(StandardCharsets.UTF_8));
            FlowFile ffNestedSibling = session.create();
            ffNestedSibling = session.putAllAttributes(ffNestedSibling, makeAttributes("nestedsibling.txt", "somesubfolder", getTempFilePathForRemoveSplitFiles() + "/somesubfolder", "2"));
            testRunner.enqueue(ffNestedSibling);
            // Not nested
            Files.write(Paths.get(getTempFilePathForRemoveSplitFiles() + "/splitfile.txt"), "this represents a split file".getBytes(StandardCharsets.UTF_8));
            FlowFile ffSplitFile = session.create();
            ffSplitFile = session.putAllAttributes(ffSplitFile, makeAttributes("splitfile.txt", "/", getTempFilePathForRemoveSplitFiles(), "1"));
            testRunner.enqueue(ffSplitFile);
            // Not a split file
            Files.write(Paths.get(getTempFilePathForRemoveSplitFiles() + "/notsplitfile.txt"), "not a split file".getBytes(StandardCharsets.UTF_8));
            FlowFile ffNotSplit = session.create();
            ffNotSplit = session.putAllAttributes(ffNotSplit, makeAttributes("notsplitfile.txt", "/", getTempFilePathForRemoveSplitFiles(), ""));
            testRunner.enqueue(ffNotSplit);
        } catch(IOException e) {
            assertEquals("Error message", "", e.toString());
        }

        // ### Setup the processor
        // path is relative to where we are running tests from, identifiable via System.getProperty("user.dir")
        String groovyScript = "";
        try {
            groovyScript = loadScriptFile("../../nifi-script-processors/RemoveSplitFiles.groovy");
        } catch(IOException e) {
            assertEquals("Error message", "", e.toString());
        }
        // Customize for testing by using a different temp folder
        groovyScript = groovyScript.replaceAll("def tf = '/home/nifi/tempfiles'", "def tf = '" + getTempFilePathForRemoveSplitFiles() + "'");

        // Assign configured script to processor
        testRunner.setProperty(ExecuteGroovyScript.SCRIPT_BODY, groovyScript);        
    }

    @Test
    public void testRemoveSplitFilesScript() {

        // ### Nested splitfile
        testRunner.run(1);
        File nestedSplitFile = new File(getTempFilePathForRemoveSplitFiles() + "/somesubfolder/nestedsplitfile.txt");
        assertEquals("Nested Split File exists", false, nestedSplitFile.exists());
        assertEquals("Nested Split File Parent Folder exists", true, nestedSplitFile.getParentFile().exists());

        // ### Nested splitfile sibling
        testRunner.run(1);
        File nestedSiblingSplitFile = new File(getTempFilePathForRemoveSplitFiles() + "/somesubfolder/nestedsibling.txt");
        assertEquals("Nested Sibling Split File exists", false, nestedSiblingSplitFile.exists());
        assertEquals("Nested Sibling Split File Parent Folder exists", false, nestedSiblingSplitFile.getParentFile().exists());

        // ### Not nested split file
        testRunner.run(1);
        File splitFile = new File(getTempFilePathForRemoveSplitFiles() + "/splitfile.txt");
        assertEquals("Split File exists", false, splitFile.exists());
        assertEquals("Split File Parent Folder exists", true, splitFile.getParentFile().exists());

        // ### Not a split file
        testRunner.run(1);
        File notSplitFile = new File(getTempFilePathForRemoveSplitFiles() + "/notsplitfile.txt");
        assertEquals("Not Split File exists", true, notSplitFile.exists());
        assertEquals("Not Split File Parent Folder exists", true, notSplitFile.getParentFile().exists());

        // ### Check empty
        testRunner.assertQueueEmpty();        
    }

    @After
    public void cleanup() throws IOException {
        // Delete the remove split files temp folder
        FileUtils.deleteDirectory(new File(getTempFilePathForRemoveSplitFiles()));
    }
}


