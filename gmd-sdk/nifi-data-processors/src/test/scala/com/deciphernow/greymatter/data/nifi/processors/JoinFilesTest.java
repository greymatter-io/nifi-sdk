package com.deciphernow.greymatter.data.nifi.processors;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.security.MessageDigest;  

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

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

public class JoinFilesTest {
    private TestRunner testRunner;
    private int fps = 4;
    private String theTestFile = "JoinFilesTestFile" + Long.toString(System.nanoTime()) + ".txt";
    private String fileContent = "This is some overall test content\n";
    private String expectedSHA256 = "53933e6eeb0ae9abc3bffbfe3c7f58b772a3f416072c551720a3dc1193e9f65d";
    private Boolean isLinux = (System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH).indexOf("nux") >= 0);

    private String getTempFilePathForJoinFiles() {
        return "/tmp/JoinFilesTest";
    }

    // Based on idx provided, returns strings like filepart_aaa through filepart_zzz
    private String getFilePartName(int idx) {
        int lia = 26, co=96; // letters in alphabet, ascii offset
        int p1 = ((idx-1) / (lia * lia)) + 1;
        int p2 = (((idx-1) - ((p1 - 1) * (lia * lia))) / lia) + 1;
        int p3 = idx - ((p1-1)*lia*lia) - ((p2-1)*lia);
        return "filepart_" + (char)(co+p1) + (char)(co+p2) + (char)(co+p3);
    }

    private String getSHA256Hash(String data) {
        String result = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            return DatatypeConverter.printHexBinary(hash).toLowerCase();
        }catch(Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    @Before
    public void init() {
        if(!isLinux) return;

        // Make things verbose:
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.standard", "debug");
        testRunner = TestRunners.newTestRunner(ExecuteGroovyScript.class);

        // verify that our expected hash which was gotten at the command line is the same when using SHA-256 message digest
        assertEquals("hash matches", expectedSHA256, getSHA256Hash(fileContent));

        // ### Setup file parts in a subfolder in /tmp
        try {
            int idx = 0;
            String fc = fileContent;
            int l = fc.length();
            while(l > 0) {
                idx ++;
                // file content
                int pl = fc.length();
                if(pl >= fps) pl = fps;
                String fileContentPart = fc.substring(0,pl);
                String fileContentPartName = getFilePartName(idx);
                String fileContentPartNamePath = getTempFilePathForJoinFiles() + "/" + theTestFile + "/" + fileContentPartName;
                File fcpn = new File(fileContentPartNamePath);
                fcpn.getParentFile().mkdirs();
                Files.write(Paths.get(fileContentPartNamePath), fileContentPart.getBytes(StandardCharsets.UTF_8));
                if(pl < l) {
                    fc = fc.substring(fps);
                } else {
                    fc = "";
                }
                l = fc.length();
                // attributes
                ProcessSession session = testRunner.getProcessSessionFactory().createSession();
                FlowFile ff = session.create();
                Map<String, String> attributes = new LinkedHashMap<String, String>();
                attributes.put("baseOutputDirectory", getTempFilePathForJoinFiles());
                attributes.put("filename", fileContentPartName);
                attributes.put("file.size", Long.toString(pl));
                attributes.put("path", "/" + theTestFile + "/");
                ff = session.putAllAttributes(ff, attributes);
                testRunner.enqueue(ff);
            }
            // And the terminator file
            String fileContentPartName = "filepart_zzz";
            String fileContentPartNamePath = getTempFilePathForJoinFiles() + "/" + theTestFile + "/" + fileContentPartName;
            Files.write(Paths.get(fileContentPartNamePath), "".getBytes(StandardCharsets.UTF_8));
            ProcessSession session = testRunner.getProcessSessionFactory().createSession();
            FlowFile ff = session.create();
            Map<String, String> attributes = new LinkedHashMap<String, String>();
            attributes.put("baseOutputDirectory", getTempFilePathForJoinFiles());
            attributes.put("filename", fileContentPartName);
            attributes.put("file.size", Long.toString(0));
            attributes.put("path", "/" + theTestFile + "/");
            ff = session.putAllAttributes(ff, attributes);
            testRunner.enqueue(ff);            

        } catch(IOException e) {
            assertEquals("Error message", "", e.toString());
        }

        // ### Setup the processor
        // path is relative to where we are running tests from, identifiable via System.getProperty("user.dir")
        String groovyScript = "";
        try {
            groovyScript = loadScriptFile("../../nifi-script-processors/JoinFiles.groovy");
        } catch(IOException e) {
            assertEquals("Error message", "", e.toString());
        }
        // Customize for having been split on smaller file size
        groovyScript = groovyScript.replaceAll("def fps = '4G' //", "def fps = '" + Integer.toString(fps) + "' //");

        // Assign configured script to processor
        testRunner.setProperty(ExecuteGroovyScript.SCRIPT_BODY, groovyScript);        
    }

    @After
    public void cleanup() throws IOException {
        if(!isLinux) return;

        // Delete the join files temp folder
        FileUtils.deleteDirectory(new File(getTempFilePathForJoinFiles()));
    }

    private String loadScriptFile(String path) throws IOException {
        Charset encoding = StandardCharsets.UTF_8;
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    @Test
    public void testJoinFilesScript() {
        if(!isLinux) return;

        // Run through the processor
        int runCount = (fileContent.length() / fps);
        runCount += (runCount*fps == fileContent.length() ? 0 : 1);
        runCount += 1;
        assertEquals("number to run", "10", Integer.toString(runCount));
        for(int i = 0; i < runCount; i++) testRunner.run(1);
        testRunner.assertQueueEmpty();

        // Verify existence of file
        String joinedFilePath = getTempFilePathForJoinFiles() + "/" + theTestFile;
        File f = new File(joinedFilePath);
        assertEquals("File exists", true, f.exists());
        
        // Read the contents
        String joinedContent = "";
        try
        {
            joinedContent = new String ( Files.readAllBytes( Paths.get(joinedFilePath) ) );
        } 
        catch (IOException e) 
        {
            assertEquals("Error message", "", e.toString());
        }
        assertEquals("File content", fileContent, joinedContent);

        // Do the sha256 check
        assertEquals("hash matches", expectedSHA256, getSHA256Hash(joinedContent));

        
    }
}


