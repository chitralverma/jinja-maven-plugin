/*
 *    Copyright 2021 Chitral Verma
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.github.chitralverma.jinja.maven.plugin;

import static com.github.chitralverma.jinja.maven.plugin.PluginConstants.*;

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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * {@link GenerateFromJinjaTemplateMojo}
 *
 * <p>This mojo automatically renders concrete resources from Jinja template files as part of Maven
 * build process.
 *
 * <p>Users define template file(s) and corresponding value file(s). When the plugin executes, it
 * substitutes the values from value file(s) in the template file(s) and renders concrete
 * resource(s) at the configured location.
 *
 * <p>This plugin uses jinjava, see <a href="https://github.com/HubSpot/jinjava#jinjava"
 * target="_blank"> this link</a> for more info.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GenerateFromJinjaTemplateMojo extends AbstractMojo {

  /** Configuration to skip the entire goal. Default: false */
  @Parameter(property = "jinja-maven.skip", defaultValue = "false")
  private final Boolean skip = Boolean.FALSE;

  /** Configuration to fail if values for template are missing. Default: true */
  @Parameter(property = "jinja-maven.failOnMissingValues", defaultValue = "true")
  private final Boolean failOnMissingValues = Boolean.TRUE;

  /** Configuration to control if output files can be overwritten. Default: false */
  @Parameter(property = "jinja-maven.overwriteOutput", defaultValue = "false")
  private final Boolean overwriteOutput = Boolean.FALSE;

  /**
   * Configuration for resource set. A resource set is bundle of one or more resources which can be
   * translated to a rendering job. It contains a template file path, one or more value files and an
   * output file path.
   */
  @Parameter private final List<ResourceBean> resourceSet = Collections.emptyList();

  /**
   * Entry point to rendering logic. The whole process can be optionally skipped if required using
   * the `Skip` configuration.
   *
   * <p>Step 1: Perform validation of configuration values provided by the user. Step 2: For each
   * resource bundle, render the concrete files as per the provided template by substituting values
   * from the value files. Step 3: Write concrete outputs as files.
   *
   * @throws MojoExecutionException Rendering errors result in `MojoExecutionException`
   * @throws MojoFailureException Validations errors result in `MojoFailureException`
   */
  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().debug("Plugin execution begins.");

    if (Boolean.TRUE.equals(skip)) {
      getLog()
          .warn(
              String.format(
                  "%s:%s is skipped as %s=true", PluginGoalPrefix, DefaultPluginGoal, Skip));
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

  /** Prints the configuration values provided by the user to debug level. */
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

  /**
   * Validates the configuration values provided by the user.
   *
   * @throws MojoFailureException Validations errors result in `MojoFailureException`
   */
  private void validate() throws MojoFailureException {
    getLog().debug("Starting validations.");

    validateResourceSet();
    getLog().debug("Validations complete");
  }

  /**
   * Validates the resource set values provided by the user.
   *
   * @throws MojoFailureException Validations errors result in `MojoFailureException`
   */
  private void validateResourceSet() throws MojoFailureException {
    if (resourceSet.isEmpty()) {
      throw new MojoFailureException(
          "Error occurred during configuration validation.",
          new IllegalArgumentException(
              String.format("'%s' must be defined with at least 1 resource.", resourceSet)));
    }

    for (ResourceBean resource : resourceSet) {
      getLog().debug(String.format("Validating resource '%s'", resource));
      validateResource(resource);
    }
  }

  /**
   * Validates a resource of resource set as defined by the user.
   *
   * @throws MojoFailureException Validations errors result in `MojoFailureException`
   */
  private void validateResource(ResourceBean resource) throws MojoFailureException {
    if (resource == null) {
      throw new MojoFailureException(
          "Error occurred during configuration validation.",
          new IllegalArgumentException("Malformed 'resource' was encountered."));
    }

    validateFile("templateFilePath", resource.getTemplateFilePath());

    if (resource.getValueFiles().isEmpty()) {
      throw new MojoFailureException(
          "Error occurred during configuration validation.",
          new IllegalArgumentException("'valueFiles' must be defined with at least 1 path."));
    }

    for (File file : resource.getValueFiles()) {
      validateFile("valueFile", file);
    }

    validateOutputFile(resource.getOutputFilePath());
  }

  /**
   * Validates a file based on path provided by the user for a resource.
   *
   * @throws MojoFailureException Validations errors result in `MojoFailureException`
   */
  private void validateFile(String key, File file) throws MojoFailureException {
    if (file == null) {
      throw new MojoFailureException(
          "Error occurred during configuration validation.",
          new IllegalArgumentException(String.format("'%s' path must not be null.", key)));
    }

    if (!file.exists()) {
      throw new MojoFailureException(
          "Error occurred during configuration validation.",
          new IllegalArgumentException(String.format("'%s' path '%s' must exist.", key, file)));
    }

    if (!file.isFile()) {
      throw new MojoFailureException(
          "Error occurred during configuration validation.",
          new IllegalArgumentException(String.format("'%s' path '%s' must be a file.", key, file)));
    }
  }

  /**
   * Validates rules are different for output files.
   *
   * @throws MojoFailureException Validations errors result in `MojoFailureException`
   */
  private void validateOutputFile(File file) throws MojoFailureException {
    if (file == null) {
      throw new MojoFailureException(
          "Error occurred during configuration validation.",
          new IllegalArgumentException("'outputFilePath' path must not be null."));
    }

    if (file.exists()) {
      if (file.isDirectory()) {
        throw new MojoFailureException(
            "Error occurred during configuration validation.",
            new IllegalArgumentException(
                String.format("'outputFilePath' path '%s' must be a file.", file)));
      } else if (!overwriteOutput) {
        throw new MojoFailureException(
            "Error occurred during configuration validation.",
            new IllegalArgumentException(
                "Overwriting output files has been disabled in plugin config."
                    + " Set 'overwriteOutput' config to true to allow this."));
      }

      getLog()
          .warn(
              String.format(
                  "'outputFilePath' path '%s' already exists and will be overwritten.", file));
    }
  }

  /**
   * Rendering logic using Jinjava.
   *
   * <p>Value file(s) are read as JSON Objects using jackson and all the nodes are iterated add keys
   * and typed values to a common context which will hold all values for substitution into the
   * template.
   *
   * <p>Reason to choose JSON format for value files: - Type safety of values - Unstructured -
   * Support complex types - Human readable and popular
   *
   * <p>Once the rendering is complete, errors are thrown if required.
   *
   * @throws MojoExecutionException Rendering errors result in `MojoFailureException`
   */
  private String renderFromResource(ResourceBean resource) throws MojoExecutionException {
    JinjavaConfig jc =
        JinjavaConfig.newBuilder().withFailOnUnknownTokens(failOnMissingValues).build();
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
            throw new MojoExecutionException(
                "Error occurred during resource rendering.",
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
        throw new MojoExecutionException(
            "Error occurred during resource rendering.",
            new IllegalArgumentException(
                renderResult.getErrors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","))));

      } else {
        return renderResult.getOutput();
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Error occurred during resource rendering.", e);
    }
  }

  /**
   * Writes the rendered content to a file.
   *
   * @param outputFile Output file as defined in some resource.
   * @param renderedContent Content to be written to file as output.
   * @throws MojoExecutionException `IOException` are recorded if any.
   */
  private void writeOutput(File outputFile, String renderedContent) throws MojoExecutionException {
    try {
      FileUtils.writeStringToFile(outputFile, renderedContent, StandardCharsets.UTF_8, false);
    } catch (IOException e) {
      throw new MojoExecutionException("Error occurred while writing output.", e);
    }
  }
}
