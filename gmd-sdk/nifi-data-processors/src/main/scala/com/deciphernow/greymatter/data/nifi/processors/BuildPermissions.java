package com.deciphernow.greymatter.data.nifi.processors;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.util.StandardValidators;

//import com.google.gson.*;

//import org.json.JSONObject;
//import org.json.JSONArray;

import java.util.*;
import java.util.regex.Pattern;

@Tags({"gmdata"})
@CapabilityDescription("A processor to replicate unix like file permissions of a file to upload to GM-Data.")
@SeeAlso({PrepareWriteRequest.class})
@ReadsAttributes({
    @ReadsAttribute(attribute = "file.owner", description="The owner of the file as it appears on the Unix system file permissions. The user who owns the file"),
    @ReadsAttribute(attribute = "file.group", description="The group of the file as it appears on the Unix system file permissions. The group who has access to the file"),
    @ReadsAttribute(attribute = "file.permissions", description="The permissions of the file as it appears on the Unix system file permissions. For example \"rw-r--r--\"."),
})
@WritesAttributes({
    @WritesAttribute(attribute = "permission", description="A permission structure that can be sent to the Data Policy Converter along with an access control model."),
    @WritesAttribute(attribute = "buildpermissions.java.exception.class", description = "The Java exception class raised when the processor fails"),
    @WritesAttribute(attribute = "buildpermissions.java.exception.message", description = "The Java exception message raised when the processor fails"),
})
public class BuildPermissions extends AbstractProcessor {
    public final static String FILE_OWNER = "file.owner";
    public final static String FILE_GROUP = "file.group";
    public final static String FILE_PERMISSIONS = "file.permissions";
    public final static String PERMISSION = "permission";
    public final static String EXCEPTION_CLASS = "buildpermissions.java.exception.class";
    public final static String EXCEPTION_MESSAGE = "buildpermissions.java.exception.message";

    public static final Relationship SUCCESS = new Relationship.Builder()
            .name("Success")
            .description("A successful relationship. Successfully built the permissions json object.")
            .build();

    public static final Relationship FAILURE = new Relationship.Builder()
            .name("Failure")
            .description("A failed relationship. Failed to build the permissions json object.")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(FilePermissionsProperty);
        descriptors.add(FileOwnerProperty);
        descriptors.add(FileGroupProperty);
        descriptors.add(FileOtherProperty);
        descriptors.add(ResourcesProperty);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(SUCCESS);
        relationships.add(FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    public static final PropertyDescriptor FileOwnerProperty = new PropertyDescriptor.Builder()
            .name("File Owner")
            .description("The user identifier of the file")
            .required(true)
            .defaultValue("${" + FILE_OWNER + "}")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor FileGroupProperty = new PropertyDescriptor.Builder()
            .name("File Group")
            .description("The group identifier of the file")
            .required(true)
            .defaultValue("${" + FILE_GROUP + "}")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor FileOtherProperty = new PropertyDescriptor.Builder()
            .name("File Other")
            .description("The other / all users identifier of the file")
            .required(true)
            .defaultValue("group/_everyone")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor FilePermissionsProperty = new PropertyDescriptor.Builder()
            .name("File Permissions")
            .description("The permissions string (e.g. rwxr-xr-x) of the file")
            .required(true)
            .defaultValue("${" + FILE_PERMISSIONS + "}")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor ResourcesProperty = new PropertyDescriptor.Builder()
            .name("Resource Mapping")
            .description("Used for overriding the value of the File Owner or Group with a replacement value from a JSON structure in the format {name1:newvalue1,name2:newvalue2}. " +
                    "For example {\"daveborncamp\": \"user/cn=daveborncamp,o=whatever,c=us\", \"engineers\": \"group/decipher/engineers\"} " +
                    "maps any " + FILE_OWNER + " or " + FILE_GROUP + " named daveborncamp to user/cn=daveborncamp,o=whatever,c=us in the final" +
                    "permissions structure. Likewise in this sample, if the " + FILE_OWNER + " or " + FILE_GROUP + " value is engineers, it would be mapped to group/decipher/engineers.")
            .required(false)
            .defaultValue("{\"root\":\"user/cn=rootuser\"}")
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        final ComponentLog logger = this.getLogger();
        FlowFile flowFile = session.get();

        if (flowFile == null) {            
            return;
        }

        // start up permissions
        PermissionsJson permissionJson = new PermissionsJson();

        String resourceProp = context.getProperty(ResourcesProperty).evaluateAttributeExpressions(flowFile).getValue();
        Resources resources = new Resources();
        if (resourceProp != null){
            try {
                resources = new Resources(resourceProp);
                logger.debug("The parsed resource: "+ resources);
            } catch (JsonProcessingException e) {
                logger.warn("The given resource property: "+resourceProp);
                logger.error(e.toString());
                throw new ProcessException("ResourcesProperty Json not parsable. Check formatting");
            }
        }
        PermissionsWork permissionsWorker = new PermissionsWork(logger, resources, permissionJson);

        // Get all of the things needed to do work.
        // For reference the basic 3 things in any flowfile are path, filename, and uuid
        logger.debug("Input flowfile: " + flowFile.getAttributes().toString());
        String owner = null;
        String group = null;
        String filePermissions = null;
        String otherStr = null;
        try {
            filePermissions = context.getProperty(FilePermissionsProperty).evaluateAttributeExpressions(flowFile).getValue();
            if (filePermissions == null) {
                filePermissions = flowFile.getAttribute("file.permissions");
            }
            logger.debug("File Permissions property is: " + filePermissions);
            // Could have the leading dash or 'd' for directory or not
            switch (filePermissions.length()) {
                case 9:
                    logger.debug("file permissions length is correct at 9");
                    break;
                case 10:
                    if (filePermissions.startsWith("-") | filePermissions.startsWith("d") | filePermissions.startsWith("s")) {
                        logger.debug("Found length 10 file permissions");
                        filePermissions = filePermissions.substring(1, 10);

                    } else {
                        throw new Exception("Length of file.permissions is 10 but did not start with '-', 'd', or 's' so it is not valid");
                    }
                    break;

                default:
                    throw new Exception("Value for file.permissions is an unsupported length");
            }

            // start with owner
            String permission = filePermissions.substring(0, 3);
            owner = context.getProperty(FileOwnerProperty).evaluateAttributeExpressions(flowFile).getValue();
            if (owner == null) {
                owner = flowFile.getAttribute(FILE_OWNER);
            }
            permissionsWorker.addPermissions(permission, owner);

            // Next is group
            permission = filePermissions.substring(3, 6);
            group = context.getProperty(FileGroupProperty).evaluateAttributeExpressions(flowFile).getValue();
            if (group == null) {
                group = flowFile.getAttribute(FILE_GROUP);
            }
            permissionsWorker.addPermissions(permission, group);

            // Next is others
            permission = filePermissions.substring(6, 9);
            otherStr = context.getProperty(FileOtherProperty).evaluateAttributeExpressions(flowFile).getValue();
            if (otherStr == null) {
                otherStr = "group/_everyone";
            }
            permissionsWorker.addPermissions(permission, otherStr);

            logger.debug("Properties used: group: "+ group + " owner: " + owner + " File permissions: " + filePermissions + " Other string: " + otherStr);
        } catch (Exception e) {
            logger.error("Routing to {} due to exception: {}", new Object[]{FAILURE.getName(), e}, e.fillInStackTrace());
            logger.error("Trace:");
            e.printStackTrace();
            flowFile = session.penalize(flowFile);
            flowFile = session.putAttribute(flowFile, EXCEPTION_CLASS, e.getClass().getName());
            flowFile = session.putAttribute(flowFile, EXCEPTION_MESSAGE, e.getMessage());
            // transfer original to failure
            session.transfer(flowFile, FAILURE);
        }        

        logger.debug("permissionJson: " + permissionsWorker.getPermissionsJson());
        logger.debug("Flowfile before: " + flowFile.getAttributes());

        String returnJson = null;
        try{
            returnJson = permissionsWorker.getPermissionsJsonString();
        } catch (JsonProcessingException e) {
            logger.error(e.toString());
        }

        session.putAttribute(flowFile, PERMISSION, returnJson);
        session.removeAttribute(flowFile, FILE_OWNER);
        session.removeAttribute(flowFile, FILE_GROUP);
        session.removeAttribute(flowFile, FILE_PERMISSIONS);

        session.transfer(flowFile, SUCCESS);

    }
}


class Resources {
    private List<Resource> resources;

    public Resources(String json) throws JsonProcessingException {
        resources = new ArrayList<Resource>();
        System.out.println("Got: "+json);
        this.parse(json);
    }

    public Resources(){
        resources = new ArrayList<Resource>();
    }

    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public String getValue(String name){
        for (Resource r : resources){
            if (name.equals(r.getName())) {
                return r.getValue();
            }
        }
        return null;
    }

    private void parse(String json) throws JsonProcessingException {
        JsonFactory factory = new JsonFactory();

        ObjectMapper mapper = new ObjectMapper(factory);
        JsonNode rootNode = mapper.readTree(json);

        Iterator<Map.Entry<String,JsonNode>> fieldsIterator = rootNode.fields();
        while (fieldsIterator.hasNext()) {
            Map.Entry<String,JsonNode> field = fieldsIterator.next();
            Resource resource = new Resource(field.getKey(), field.getValue().textValue());

            this.resources.add(resource);
        }
    }
}

class Resource {
    private String name;
    private String value;

    public Resource(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}

class Allow{
    // Don't allow duplicates, use hashset
    private HashSet<String> allow;

    Allow(){
        allow = new HashSet<String>();
    }

    public void add_allow(String name){
        allow.add(name);
    }

    public HashSet<String> getAllow() {
        return allow;
    }

    public void setAllow(HashSet<String> allow) {
        this.allow = allow;
    }
}


/**
 * Class to contain the permission structure that will be added to the flowfile.
 * This is needed for Jackson to properly serialize a json file.
 */
class PermissionsJson {
    private Allow create;
    private Allow read;
    private Allow update;
    private Allow delete;

    public PermissionsJson() {
        this.create = new Allow();
        this.read = new Allow();
        this.update = new Allow();
        this.delete = new Allow();
    }

    public Allow getCreate() {
        return create;
    }

    public Allow getRead() {
        return read;
    }

    public Allow getUpdate() {
        return update;
    }

    public Allow getDelete() {
        return delete;
    }

    public void setCreate(Allow create) {
        this.create = create;
    }

    public void setRead(Allow read) {
        this.read = read;
    }

    public void setUpdate(Allow update) {
        this.update = update;
    }

    public void setDelete(Allow delete) {
        this.delete = delete;
    }
    public void updateCreate(String name) {
        this.create.add_allow(name);
    }

    public void updateRead(String name) {
        this.read.add_allow(name);
    }

    public void updateUpdate(String name) {
        this.update.add_allow(name);
    }

    public void updateDelete(String name) {
        this.delete.add_allow(name);
    }
}

class PermissionsWork{
    private final Resources resources;
    private final ComponentLog logger;
    private PermissionsJson permissionsJson;

    public PermissionsWork(ComponentLog logger, Resources resources, PermissionsJson permissionsJson) {
        this.logger = logger;
        this.resources = resources;
        this.permissionsJson = permissionsJson;
    }

    public PermissionsJson getPermissionsJson() {
        return permissionsJson;
    }

    public String getPermissionsJsonString() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(permissionsJson);
    }

    /**
     * Add permissions to the permissionJson based on the string 'rwx' for either user, group, or everyone
     *
     * @param permission - The permission string part. For example 'r--', 'rwx', 'r-x', etc.
     * @param allowStr - The string to allow for the given permission ex 'group/_everyone'
     */
    protected void addPermissions(String permission, String allowStr){
        // Validation
        allowStr = allowStr.trim();
        if (allowStr.length() == 0) {
            return;
        }
        if (permission.length() != 3) {
            return;
        }
        Pattern pattern = Pattern.compile("^[r-][w-][x-]$");
        if (!pattern.matcher(permission).matches()) {
            return;
        }

        // Handle resource mapping
        try {
            String mappedValue = resources.getValue(allowStr);
            if (mappedValue.length() > 0) {
                logger.warn("Found " + mappedValue + " in resources for " + allowStr + ". Updating with replacement value");
                allowStr = mappedValue;
            }
        } catch (Exception e) {
            logger.debug(allowStr + " not found in resource mapping. Retaining value");
        }

        // Add permissions
        logger.debug("adding permissions: "+permission + " allowed: "+ allowStr);
        if (permission.contains("r") ) {
            logger.debug("found read");

            permissionsJson.updateRead(allowStr);
        }
        if (permission.contains("w") ) {
            logger.debug("found write");

            permissionsJson.updateCreate(allowStr);
            permissionsJson.updateUpdate(allowStr);
            permissionsJson.updateDelete(allowStr);
        }
        if (permission.contains("x") ) {
            logger.debug("found execute, but doing nothing to the permissions.");
        }
    }
}