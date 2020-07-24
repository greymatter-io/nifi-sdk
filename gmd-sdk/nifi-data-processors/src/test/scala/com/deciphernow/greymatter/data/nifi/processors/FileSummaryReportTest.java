package com.deciphernow.greymatter.data.nifi.processors;

import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;

import java.util.*;

import org.apache.commons.io.FileUtils;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processors.groovyx.ExecuteGroovyScript;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class FileSummaryReportTest {
    private final String fileName= "FileSummaryReport.csv";
    private final String tempDir = "/tmp/FileSummaryReportTest/testFile";
    private final String csvfile = tempDir + "/" + fileName;
    final Logger logger = LoggerFactory.getLogger(FileSummaryReportTest.class);

    private TestRunner testRunner;
    // to hold info for verification in the csvfile
    private FlowFileInfo[] files = new FlowFileInfo[4];


    private String loadScriptFile(String path) throws IOException {
        Charset encoding = StandardCharsets.UTF_8;
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    @Before
    public void init() {
        // Make things verbose in the logs
        // the logs live at nifi-sdk/gmd-sdk/nifi-data-processors/target/surefire-reports/com.deciphernow.greymatter.data.nifi.processors.FileSummaryReportTest-output.txt
        System.setProperty("org.slf4j.simpleLogger.log.nifi.processors.standard", "debug");

        testRunner = TestRunners.newTestRunner(ExecuteGroovyScript.class);

        // Make a place for the csv file
        // It could be left over from a failed run that did not hit @After
        try {
            File directory = new File(tempDir);
            if (! directory.exists()){
                directory.mkdirs();
            }
            //Files.write(, fileName.getBytes());
            Files.deleteIfExists(Paths.get(csvfile));
        } catch(IOException e) {
            Assert.fail();
        }

        // Handle to session
        ProcessSession session = testRunner.getProcessSessionFactory().createSession();

        // make some files with the info we need
        files[0] = new FlowFileInfo("testFile", "/a/real/file/", "1", "testFile", tempDir, "/a/real/file");
        files[1] = new FlowFileInfo("DifferentFile", "/a/real/file/", "1", "DifferentFile", tempDir, "/a/real/file");
        files[2] = new FlowFileInfo("splitfile_aaa", "/this/is/new/path/", "1", "splitfile", tempDir, "/this/is/new/path");
        files[3] = new FlowFileInfo("splitfile_aab", "/this/is/new/path/", "2", "splitfile", tempDir, "/this/is/new/path");


        for (FlowFileInfo fileInfo: files){
            FlowFile fFile = session.create();
            fFile = session.putAllAttributes(fFile, fileInfo.getFlowAttributes());
            testRunner.enqueue(fFile);
            logger.info("Queuing the flowfile, the queue is now: "+testRunner.getQueueSize());
        }


        // ### Setup the processor
        // path is relative to where we are running tests from, identifiable via System.getProperty("user.dir")
        String groovyScript = "";
        try {
            groovyScript = loadScriptFile("../../nifi-script-processors/FileSummaryReport.groovy");
        } catch(IOException e) {
            e.printStackTrace();
            System.out.println(e.toString());
            Assert.fail();
        }
        groovyScript = groovyScript.replaceAll("def csvfile = '/home/nifi/reports/failed.csv'", "def csvfile = '" + csvfile + "'");

        // Assign configured script to processor
        testRunner.setProperty(ExecuteGroovyScript.SCRIPT_BODY, groovyScript);
    }

    @Test
    public void testSummaryReport() {
        // Test that the file got created properly
        logger.info("Running the flow. the queue is now: " + testRunner.getQueueSize());

        testRunner.run(4);
        logger.info("After running the flow. the queue is now: " + testRunner.getQueueSize());

        testRunner.assertQueueEmpty();

        /*
         Check that things were written to the file
         It should look like:
        "logdate","file","size","uuid","splitpart","originalfilename"
        "Wed Jun 24 15:05:16 MDT 2020","/tmp/FileSummaryReportTest/testFile:SummaryReportTest.csv/testFile",null,"e8d35a0a-86bb-460d-9f26-7a2494c479ee",1,"testFile"
        */
        try (Scanner scanner = new Scanner(new File(csvfile));) {
            int lineNum = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (lineNum == 0) {
                    assertEquals("Expected to get the header line first", "\"logdate\",\"file\",\"size\",\"uuid\",\"splitpart\",\"originalfilename\"", line);
                    lineNum++;
                    continue;
                }
                //String[] splitString = line.split(",");
                StringTokenizer splitString = new StringTokenizer(line, ",");
                logger.info("line: "+line);
                logger.info("Date: '"+ splitString.nextToken()+"'");
                assertEquals("Check that the absolute path is correct", Quote(files[lineNum - 1].absolutePath+files[lineNum - 1].filename), String.valueOf(splitString.nextToken()));
                Object nullValue = null;
                assertEquals("Check that the size is correct (not an actual file so it is null)", String.valueOf(nullValue), splitString.nextToken());
                logger.info("UUID: '"+ splitString.nextToken()+"'");
                assertEquals("Check that the split part is correct", files[lineNum - 1].splitPart, String.valueOf(splitString.nextToken()));
                assertEquals("Check that the filename is correct", Quote(files[lineNum - 1].splitOriginalfilename), splitString.nextToken());

                lineNum++;
            }
        } catch (FileNotFoundException e) {
            fail("The file: " + csvfile + " does not exist, it should.");
        }
    }

    @After
    public void cleanup() throws IOException {
        // Delete the remove split files temp folder
        FileUtils.deleteDirectory(new File(tempDir));
    }

    private String Quote(String str){
        return "\""+str+"\"";
    }


    private static class FlowFileInfo {
    String filename;
    String absolutePath;
    String splitPart;
    String splitOriginalfilename;
    String baseOutputDirectory;
    String path;

    public FlowFileInfo(String filename, String absolutePath, String splitPart, String splitOriginalfilename, String baseOutputDirectory, String path) {
        this.filename = filename;
        this.absolutePath = absolutePath;
        this.splitPart = splitPart;
        this.splitOriginalfilename = splitOriginalfilename;
        this.baseOutputDirectory = baseOutputDirectory;
        this.path = path;
    }


    public Map<String, String> getFlowAttributes(){
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("filename", filename);
        attributes.put("absolute.path", absolutePath);
        attributes.put("split.part", splitPart);
        attributes.put("split.originalfilename", splitOriginalfilename);
        attributes.put("baseOutputDirectory", baseOutputDirectory);
        attributes.put("path", path);

        return attributes;
    }
}
}
