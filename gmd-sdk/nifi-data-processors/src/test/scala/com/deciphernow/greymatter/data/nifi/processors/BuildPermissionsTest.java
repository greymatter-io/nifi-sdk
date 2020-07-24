package com.deciphernow.greymatter.data.nifi.processors;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.google.gson.*;
import org.apache.nifi.documentation.init.NopComponentLog;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;

public class BuildPermissionsTest {
    private TestRunner testRunner;

    @Before
    public void init() {
        testRunner = TestRunners.newTestRunner(BuildPermissions.class);
    }

    // Test the happy path
    @Test
    public void testPermissionsProcessor() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String fileGroup = "staff";
        String filePermissions = "rw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.group", fileGroup);
        mapped.put("file.permissions", filePermissions);

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.SUCCESS);

        // we only ran once, make sure there is only one returned
        assertEquals("There should only be 1 match", result.size(), 1);


        // make sure the permissions object was properly made
        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());

        // make sure things were removed from the flowfile
        assertEquals(flowFile.getAttributes().size(), 4);

        // Make sure the json was made properly as a json
        JsonObject jsonObject = new JsonParser().parse(flowFile.getAttribute("permission")).getAsJsonObject();

        // make sure that the permissions was actually created and assert some correctness
        assertEquals("Should have 4 keys, Read, create, update, delete", jsonObject.keySet().size(), 4);
        assertTrue("Should have Read",jsonObject.keySet().contains("read"));
        assertTrue("Should have Update",jsonObject.keySet().contains("update"));
        assertTrue("Should have Create",jsonObject.keySet().contains("create"));
        assertTrue("Should have Delete",jsonObject.keySet().contains("delete"));

    }

    @Test
    public void testPermissionsProcessorWith10Permissions() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String fileGroup = "staff";
        String filePermissions = "-rw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.group", fileGroup);
        mapped.put("file.permissions", filePermissions);

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.SUCCESS);

        // we only ran once, make sure there is only one returned
        assertEquals("There should only be 1 match", result.size(), 1);


        // make sure the permissions object was properly made
        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());

        // make sure things were removed from the flowfile
        assertEquals(flowFile.getAttributes().size(), 4);

        // Make sure the json was made properly as a json
        JsonObject jsonObject = new JsonParser().parse(flowFile.getAttribute("permission")).getAsJsonObject();

        // make sure that the permissions was actually created and assert some correctness
        assertEquals("Should have 4 keys, Read, create, update, delete", jsonObject.keySet().size(), 4);
        assertTrue("Should have Read",jsonObject.keySet().contains("read"));
        assertTrue("Should have Update",jsonObject.keySet().contains("update"));
        assertTrue("Should have Create",jsonObject.keySet().contains("create"));
        assertTrue("Should have Delete",jsonObject.keySet().contains("delete"));
    }

    // This test will test for failure
    @Test(expected = AssertionError.class)
    public void testPermissionsProcessorWith11Permissions() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String fileGroup = "staff";
        String filePermissions = "-rw-r--r--h";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.group", fileGroup);
        mapped.put("file.permissions", filePermissions);

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.run(1);  // should fail here
        testRunner.assertQueueEmpty();  // should not reach this line

    }

    // This test will test for failure
    @Test(expected = AssertionError.class)
    public void testPermissionsProcessorWith10IncorrectPermissions() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String fileGroup = "staff";
        String filePermissions = "frw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.group", fileGroup);
        mapped.put("file.permissions", filePermissions);

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.run(1);  // should fail here
        testRunner.assertQueueEmpty();  // should not reach this line

    }


    // This test will test for failure
    @Test
    public void testPermissionsProcessorMissingGroup() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String filePermissions = "rw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.permissions", filePermissions);

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.run(1);
        testRunner.assertQueueEmpty();
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.FAILURE);

        assertEquals(result.size(), 0);

    }

    // This test will test for failure due to missing things in flowfile/attributes
    @Test(expected = AssertionError.class)
    public void testPermissionsProcessorEmptyFlow() {

        // make an empty flowfile
        final InputStream content = new ByteArrayInputStream("".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();

        System.out.println("This is expected to throw an exception.");
        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.run(1);
        testRunner.assertQueueEmpty();
    }

    @Test
    public void testOwnerPropertyFullyPopulatedFlow() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String fileGroup = "staff";
        String filePermissions = "-rw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.group", fileGroup);
        mapped.put("file.permissions", filePermissions);

        // resource mappings
        JSONObject resources = new JSONObject();
        resources.put("decipherer", "user/decipherer");
        resources.put("staff", "group/staff");

        String testOwner = "user/cn=daveborncamp,o=whatever,c=us" ;
        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(BuildPermissions.FileOwnerProperty, testOwner);
        testRunner.setProperty(BuildPermissions.ResourcesProperty, resources.toString());
        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.SUCCESS);

        // we only ran once, make sure there is only one returned
        assertEquals("There should only be 1 match in the results list", result.size(), 1);
        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should be 4 things in the flowfile", flowFile.getAttributes().size(), 4);
        JsonObject jsonObject = new JsonParser().parse(flowFile.getAttribute("permission")).getAsJsonObject();

        JsonArray readAllow = jsonObject.getAsJsonObject("read").getAsJsonArray("allow");
        JsonPrimitive t = new JsonPrimitive(testOwner);
        System.out.println(readAllow);
        System.out.println(readAllow.contains(t));

        assertTrue(readAllow.contains(t));
        JsonPrimitive f = new JsonPrimitive("Wrong");

        assertFalse(readAllow.contains(f));

    }


    @Test
    public void testGroupPropertyFullyPopulatedFlow() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String fileGroup = "staff";
        String filePermissions = "-rw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.group", fileGroup);
        mapped.put("file.permissions", filePermissions);

        String testGrp = "group/decipher/decipher" ;
        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(BuildPermissions.FileGroupProperty, testGrp);
        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.SUCCESS);

        // we only ran once, make sure there is only one returned
        assertEquals("There should only be 1 match in the results list", result.size(), 1);
        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should be 4 things in the flowfile", flowFile.getAttributes().size(), 4);

        // make sure it made it into the permissions
        JsonObject jsonObject = new JsonParser().parse(flowFile.getAttribute("permission")).getAsJsonObject();

        JsonArray readAllow = jsonObject.getAsJsonObject("read").getAsJsonArray("allow");
        JsonPrimitive t = new JsonPrimitive(testGrp);
//        System.out.println(readAllow);
//        System.out.println(readAllow.contains(t));

        assertTrue(readAllow.contains(t));
        JsonPrimitive f = new JsonPrimitive("Wrong");

        assertFalse(readAllow.contains(f));

    }

    @Test
    public void testGroupPropertyPartiallyPopulatedFlow() {
        // Set up the flowfile input
        String fileOwner = "decipherer";
        String filePermissions = "-rw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.permissions", filePermissions);

        String testGrp = "group/decipher/decipher" ;
        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(BuildPermissions.FileGroupProperty, testGrp);
        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.SUCCESS);

        // we only ran once, make sure there is only one returned
        assertEquals("There should only be 1 match in the results list", result.size(), 1);
        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should be 4 things in the flowfile", flowFile.getAttributes().size(), 4);

        // make sure it made it into the permissions
        JsonObject jsonObject = new JsonParser().parse(flowFile.getAttribute("permission")).getAsJsonObject();

        JsonArray readAllow = jsonObject.getAsJsonObject("read").getAsJsonArray("allow");
        JsonPrimitive t = new JsonPrimitive(testGrp);
//        System.out.println(readAllow);
//        System.out.println(readAllow.contains(t));

        assertTrue(readAllow.contains(t));
        JsonPrimitive f = new JsonPrimitive("Wrong");

        assertFalse(readAllow.contains(f));

    }

    @Test
    public void testResourcesProperty(){
        // make an empty flowfile

        JSONObject resources = new JSONObject();
        String newUser = "user/cn=daveborncamp,o=whatever,c=us";
        resources.put("dborncamp", newUser);
        // Set up the flowfile input
        String fileOwner = "dborncamp";
        String fileGroup = "staff";
        String filePermissions = "-rw-r--r--";

        final InputStream content = new ByteArrayInputStream("Content".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();
        mapped.put("file.owner", fileOwner);
        mapped.put("file.group", fileGroup);
        mapped.put("file.permissions", filePermissions);


        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(BuildPermissions.ResourcesProperty, resources.toString());

        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.SUCCESS);

        // we only ran once, make sure there is only one returned
        assertEquals("There should only be 1 match in the results list", result.size(), 1);
        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should be 4 things in the flowfile", flowFile.getAttributes().size(), 4);
    }

    @Test
    public void testAllPropertiesEmptyFlow() {
        // make an empty flowfile
        final InputStream content = new ByteArrayInputStream("".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();

        String testFilePermissions = "rw-r--r--";
        String testGrp = "group/decipher/decipher";
        String testOwner = "testme";
        String testOthers = "group/people";

        JSONObject resources = new JSONObject();
        String newUser = "user/cn=daveborncamp,o=whatever,c=us";
        resources.put("testme", newUser);

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(BuildPermissions.FilePermissionsProperty, testFilePermissions);
        testRunner.setProperty(BuildPermissions.FileGroupProperty, testGrp);
        testRunner.setProperty(BuildPermissions.FileOwnerProperty, testOwner);
        testRunner.setProperty(BuildPermissions.FileOtherProperty, testOthers);
        testRunner.setProperty(BuildPermissions.ResourcesProperty, resources.toString());

        testRunner.run(1);
        testRunner.assertQueueEmpty();

        // do some assertions for success
        final List result = testRunner.getFlowFilesForRelationship(BuildPermissions.SUCCESS);

        // we only ran once, make sure there is only one returned
        assertEquals("There should only be 1 match in the results list", result.size(), 1);
        FlowFile flowFile = (FlowFile) result.get(0);
        System.out.println("testing the output: " + flowFile.getAttributes());
        assertEquals("There should be 4 things in the flowfile", flowFile.getAttributes().size(), 4);

        // make sure it made it into the permissions
        JsonObject jsonObject = new JsonParser().parse(flowFile.getAttribute("permission")).getAsJsonObject();
        JsonArray readAllow = jsonObject.getAsJsonObject("read").getAsJsonArray("allow");

        // test for group
        JsonPrimitive tGroup = new JsonPrimitive(testGrp);
        System.out.println(readAllow);

        assertTrue(readAllow.contains(tGroup));

        // test for owner, this should be changed with the resources!
        JsonPrimitive tOwn = new JsonPrimitive(newUser);
        assertTrue(readAllow.contains(tOwn));

        // test for owner
        JsonPrimitive tOth = new JsonPrimitive(testOthers);
        assertTrue(readAllow.contains(tOth));

    }

    @Test
    public void testPermissionJson(){
        NopComponentLog log = new NopComponentLog();
        PermissionsJson permissions = new PermissionsJson(log);

        // test an incomplete permissions string, nothing should be added
        String incomplete = "r";
        String user = "dborncamp";
        permissions.addPermissions(incomplete, user);
        JSONObject jsonObject = permissions.getPermissionJson();
        JSONArray readAllow = jsonObject.getJSONObject("read").getJSONArray("allow");
        assertEquals(readAllow.length(), 0);

        String permission = "r--";

        permissions.addPermissions(permission, user);
        jsonObject = permissions.getPermissionJson();
        System.out.println(jsonObject.toString());

        // make sure that it made it
        String result = (String) jsonObject.getJSONObject("read").getJSONArray("allow").get(0);
        System.out.println(result);
        assertEquals(user, result);

        // make sure it didn't go anywhere else
        String permissionStr = "{\"read\":{\"allow\":[\""+user+"\"]},\"create\":{\"allow\":[]},\"update\":{\"allow\":[]},\"delete\":{\"allow\":[]}}";
        assertEquals(jsonObject.toString(), permissionStr);

        // make sure things are not double added
        permission = "rwx";
        user = "dborncamp";

        permissions.addPermissions(permission, user);
        jsonObject = permissions.getPermissionJson();
        System.out.println(jsonObject.toString());

        permissionStr = "{\"read\":{\"allow\":[\""+user+"\"]},\"create\":{\"allow\":[\""+user+"\"]},\"update\":{\"allow\":[\""+user+"\"]},\"delete\":{\"allow\":[\""+user+"\"]}}";
        assertEquals(jsonObject.toString(), permissionStr);

        // try resources
        JSONObject resources = new JSONObject();
        String newUser = "user/cn=daveborncamp,o=whatever,c=us";
        resources.put("dborncamp", newUser);
        String bad = "else";
        resources.put("something", bad);
        System.out.println(resources.toString());

        permissions = new PermissionsJson(log, resources);
        permissions.addPermissions(permission, user);
        jsonObject = permissions.getPermissionJson();
        System.out.println(jsonObject.toString());

        result = (String) jsonObject.getJSONObject("read").getJSONArray("allow").get(0);
        assertEquals(result, newUser);

        // "else" should not be in there
        assertFalse(jsonObject.toString().contains(bad));
    }
}