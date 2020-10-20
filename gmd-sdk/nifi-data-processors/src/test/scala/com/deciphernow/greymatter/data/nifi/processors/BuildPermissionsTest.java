package com.deciphernow.greymatter.data.nifi.processors;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.*;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import org.junit.Before;
import org.junit.Test;

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
        JsonObject resources = new JsonObject();
        resources.add("decipherer", new JsonPrimitive("user/decipherer"));
        resources.add("staff", new JsonPrimitive("group/staff"));

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
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        String newUser = "user/cn=daveborncamp,o=whatever,c=us";
        String name = "dborncamp";
        // The json will be given as an escaped string from the UI
        String testResource = "{\""+name+"\": \""+newUser+"\"}";

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
        testRunner.setProperty(BuildPermissions.ResourcesProperty, testResource);

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
        JsonArray createAllow = jsonObject.getAsJsonObject("create").getAsJsonArray("allow");

        JsonPrimitive tOwn = new JsonPrimitive(newUser);
        assertTrue(createAllow.contains(tOwn));
    }

    @Test
    public void testAllPropertiesEmptyFlow() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        String newUser = "user/cn=daveborncamp,o=whatever,c=us";
        String name = "testme";
        String testResource = "{\""+name+"\": \""+newUser+"\"}";

        // make an empty flowfile
        final InputStream content = new ByteArrayInputStream("".getBytes());
        Map<String, String> mapped = new LinkedHashMap<String, String>();

        String testFilePermissions = "rw-r--r--";
        String testGrp = "group/decipher/decipher";
        String testOwner = "testme";
        String testOthers = "group/people";

        // start the tester and send the flowfile
        testRunner.enqueue(content, mapped);
        testRunner.setProperty(BuildPermissions.FilePermissionsProperty, testFilePermissions);
        testRunner.setProperty(BuildPermissions.FileGroupProperty, testGrp);
        testRunner.setProperty(BuildPermissions.FileOwnerProperty, testOwner);
        testRunner.setProperty(BuildPermissions.FileOtherProperty, testOthers);
        testRunner.setProperty(BuildPermissions.ResourcesProperty, testResource);

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

        assertTrue(readAllow.contains(tGroup));

        // test for owner, this should be changed with the resources!
        JsonPrimitive tOwn = new JsonPrimitive(newUser);
        System.out.println("readAllow");
        System.out.println(readAllow);
        System.out.println(tOwn);

        assertTrue(readAllow.contains(tOwn));

        // test for owner
        JsonPrimitive tOth = new JsonPrimitive(testOthers);
        assertTrue(readAllow.contains(tOth));

    }

    @Test
    public void basicPermissions() {
        ObjectMapper mapper = new ObjectMapper();
        PermissionsJson permissions = new PermissionsJson();
        permissions.updateCreate("dave");
        String json = null;
        try {
            json = mapper.writeValueAsString(permissions);
        } catch ( JsonProcessingException e){
            e.printStackTrace();
            fail();
        }
        String goodResult = "{\"create\":{\"allow\":[\"dave\"]},\"read\":{\"allow\":[]},\"update\":{\"allow\":[]},\"delete\":{\"allow\":[]}}";
        System.out.println(json);
        assertTrue(goodResult.equals(json));
    }

    @Test
    public void testAllow() {
        Allow a = new Allow();
        a.add_allow("Hello");

        assertEquals(1, a.getAllow().size());

        a.add_allow("There");
        assertEquals(2, a.getAllow().size());

        a.setAllow(new HashSet<String>());
        assertEquals(0, a.getAllow().size());
    }

    @Test
    public void testResource() {
        Resource resource = new Resource("Hi", "There");
        assertEquals("Hi", resource.getName());
        assertEquals("There", resource.getValue());
        resource.setName("foo");
        resource.setValue("bar");
        assertEquals("foo", resource.getName());
        assertEquals("bar", resource.getValue());
    }

    @Test
    public void testResources() {
        String value = "\"user/cn=daveborncamp,o=whatever,c=us\", \"engineers\": \"group/decipher/engineers\"";
        String userValue = "user/cn=daveborncamp,o=whatever,c=us";
        String name = "daveborncamp";
        String testResource = "{\""+name+"\": "+value+"}";
        Resources resources = null;
        try {
            resources = new Resources(testResource);
        } catch (JsonProcessingException e){
            e.printStackTrace();
            fail();
        }

        assert resources != null;
        assertEquals(2, resources.getResources().size());

        assertEquals(userValue, resources.getValue(name));
        assertNull(resources.getValue("wrong"));
    }
}