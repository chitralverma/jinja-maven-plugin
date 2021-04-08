package com.github.chitralverma.maven.ctp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.POJONode;
import com.google.common.collect.Maps;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.RenderResult;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.chitralverma.maven.ctp.PluginConstants.*;

/**
 * {@link GenerateFromTemplateMojo}
 *
 * <p>
 * This mojo automatically renders concrete resources from Jinja template files as part of Maven build process.
 *
 * <p>
 * Users define template file(s) and corresponding value file(s). When the plugin executes, it substitutes the
 * values from value file(s) in the template file(s) and renders concrete resource(s) at the configured location.
 *
 * <p>
 * This plugin uses jinjava, see <a href="https://github.com/HubSpot/jinjava#jinjava" target="_blank">
 * this link</a> for more info.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GenerateFromTemplateMojo extends AbstractMojo {

    /**
     * <p>
     * Configuration to skip the entire goal.
     */
    @Parameter(property = "jinja-maven.skip", defaultValue = "false")
    private final Boolean skip = Boolean.FALSE;

    /**
     * <p>
     * Configuration to fail if values for template are missing.
     */
    @Parameter(property = "jinja-maven.failOnMissingValues", defaultValue = "true")
    private final Boolean failOnMissingValues = Boolean.TRUE;

    /**
     * <p>
     * Configuration to fail if values for template are missing.
     */
    @Parameter(property = "jinja-maven.overwriteOutput", defaultValue = "false")
    private final Boolean overwriteOutput = Boolean.FALSE;

    /**
     *
     */
    @Parameter
    private final List<ResourceBean> resourceSet = Collections.emptyList();

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().debug("Plugin execution begins.");

        if (Boolean.TRUE.equals(skip)) {
            getLog().warn(String.format("%s:%s is skipped as %s=true",
                    PluginGoalPrefix, DefaultPluginGoal, Skip));
        } else {
            validate();
            printConfigs();

            getLog().info("Starting resource rendering process.");
            for (ResourceBean resource : resourceSet) {
                getLog().debug(String.format("Rendering resource '%s'", resource));

                String renderedResource = renderFromResource(resource);
                writeOutput(resource.getOutputFilePath(), renderedResource);
            }

            getLog().info("Resource rendering process is complete.");
        }

        getLog().debug("Plugin execution ends.");
    }

    private void printConfigs() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        try {
            ObjectNode configuration = mapper.createObjectNode();
            configuration.set(Skip, BooleanNode.valueOf(skip));
            configuration.set(ResourceSet, new POJONode(resourceSet));
            configuration.set(FailOnMissingValues, new POJONode(failOnMissingValues));
            configuration.set(OverwriteOutput, new POJONode(overwriteOutput));

            String jsonConfig = mapper.writeValueAsString(configuration);
            getLog().debug(String.format("Plugin Config:\n%s", jsonConfig));
        } catch (JsonProcessingException e) {
            getLog().warn("Unable to print configs", e);
        }
    }

    private void validate() throws MojoFailureException {
        getLog().debug("Starting validations.");

        validateResourceSet();
        getLog().debug("Validations complete");
    }

    private void validateResourceSet() throws MojoFailureException {
        if (resourceSet.isEmpty()) {
            throw new MojoFailureException("Error occurred during configuration validation.",
                    new IllegalArgumentException(
                            String.format("'%s' must be defined with at least 1 resource.", resourceSet)));
        }

        for (ResourceBean resource : resourceSet) {
            getLog().debug(String.format("Validating resource '%s'", resource));
            validateResource(resource);
        }
    }

    private void validateResource(ResourceBean resource) throws MojoFailureException {
        if (resource == null) {
            throw new MojoFailureException("Error occurred during configuration validation.",
                    new IllegalArgumentException("Malformed 'resource' was encountered."));
        }

        validateFile("templateFilePath", resource.getTemplateFilePath());

        if (resource.getValueFiles().isEmpty()) {
            throw new MojoFailureException("Error occurred during configuration validation.",
                    new IllegalArgumentException("'valueFiles' must be defined with at least 1 path."));
        }

        for (File file : resource.getValueFiles()) {
            validateFile("valueFile", file);
        }

        validateOutputFile(resource.getOutputFilePath());
    }

    private void validateFile(String key, File file) throws MojoFailureException {
        if (file == null) {
            throw new MojoFailureException("Error occurred during configuration validation.",
                    new IllegalArgumentException(String.format("'%s' path must not be null.", key)));
        }

        if (!file.exists()) {
            throw new MojoFailureException("Error occurred during configuration validation.",
                    new IllegalArgumentException(String.format("'%s' path '%s' must exist.", key, file)));
        }

        if (!file.isFile()) {
            throw new MojoFailureException("Error occurred during configuration validation.",
                    new IllegalArgumentException(String.format("'%s' path '%s' must be a file.", key, file)));
        }
    }

    private void validateOutputFile(File file) throws MojoFailureException {
        if (file == null) {
            throw new MojoFailureException("Error occurred during configuration validation.",
                    new IllegalArgumentException("'outputFilePath' path must not be null."));
        }

        if (file.exists()) {
            if (file.isDirectory()) {
                throw new MojoFailureException("Error occurred during configuration validation.",
                        new IllegalArgumentException(
                                String.format("'outputFilePath' path '%s' must be a file.", file)));
            } else if (!overwriteOutput) {
                throw new MojoFailureException("Error occurred during configuration validation.",
                        new IllegalArgumentException("Overwriting output files has been disabled in plugin config." +
                                " Set 'overwriteOutput' config to true to allow this."));
            }

            getLog().warn(String.format("'outputFilePath' path '%s' already exists and will be overwritten.", file));
        }

    }

    private String renderFromResource(ResourceBean resource) throws MojoExecutionException {
        JinjavaConfig jc = JinjavaConfig.newBuilder().withFailOnUnknownTokens(failOnMissingValues).build();
        Jinjava jinjava = new Jinjava(jc);

        Map<String, Object> context = Maps.newHashMap();

        try {
            String templateContent =
                    FileUtils.readFileToString(resource.getTemplateFilePath(), StandardCharsets.UTF_8);

            for (File valueFile : resource.getValueFiles()) {
                ObjectMapper mapper = new ObjectMapper();
                Iterator<Map.Entry<String, JsonNode>> iter = mapper.readTree(valueFile).fields();

                while (iter.hasNext()) {
                    Map.Entry<String, JsonNode> next = iter.next();
                    JsonNodeType nodeType = next.getValue().getNodeType();

                    if (next.getKey().contains(".")) {
                        throw new MojoExecutionException("Error occurred during resource rendering.",
                                new IllegalArgumentException("Keys of value files cannot contain chars in [.]"));
                    }

                    getLog().debug(String.format("Added entry [ %s ] to context.", next));
                    if (nodeType == JsonNodeType.ARRAY || nodeType == JsonNodeType.OBJECT) {
                        context.put(next.getKey(), next.getValue());
                    } else {
                        context.put(next.getKey(), next.getValue().asText());
                    }
                }
            }

            RenderResult renderResult = jinjava.renderForResult(templateContent, context);

            if (!renderResult.getErrors().isEmpty() && failOnMissingValues) {
                throw new MojoExecutionException("Error occurred during resource rendering.",
                        new IllegalArgumentException(renderResult.getErrors().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(","))));

            } else {
                return renderResult.getOutput();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error occurred during resource rendering.", e);
        }
    }

    private void writeOutput(File outputFile, String renderedResource) throws MojoExecutionException {
        try {
            FileUtils.writeStringToFile(outputFile, renderedResource, StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            throw new MojoExecutionException("Error occurred while writing output.", e);
        }

    }
}