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
import com.github.chitralverma.jinja.maven.plugin.utils.MavenPropertiesUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.RenderResult;
import com.hubspot.jinjava.loader.CascadingResourceLocator;
import com.hubspot.jinjava.loader.ClasspathResourceLocator;
import com.hubspot.jinjava.loader.FileLocator;
import com.hubspot.jinjava.loader.ResourceLocator;
import java.io.File;
import java.io.FileNotFoundException;
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
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * {@link GenerateFromJinjaTemplateMojo}
 *
 * <p>This mojo automatically renders concrete resources from Jinja template
 * files as part of Maven build process.
 *
 * <p>Users define template file(s) and corresponding value file(s). When the
 * plugin executes, it substitutes the values from value file(s) in the template
 * file(s) and renders concrete resource(s) at the configured location.
 *
 * <p>This plugin uses jinjava, see <a
 * href="https://github.com/HubSpot/jinjava#jinjava" target="_blank"> this
 * link</a> for more info.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GenerateFromJinjaTemplateMojo extends AbstractMojo {

  /** The Maven Project Object. */
  @Component protected MavenProject project;

  /** Configuration to skip the entire goal. Default: false */
  @Parameter(property = "jinja-maven.skip", defaultValue = "false")
  private final Boolean skip = Boolean.FALSE;

  /** Configuration to fail if values for template are missing. Default: true */
  @Parameter(
      property = "jinja-maven.failOnMissingValues",
      defaultValue = "true")
  private final Boolean failOnMissingValues = Boolean.TRUE;

  /**
   * Configuration to control if output files can be overwritten. Default: false
   */
  @Parameter(property = "jinja-maven.overwriteOutput", defaultValue = "false")
  private final Boolean overwriteOutput = Boolean.FALSE;

  /**
   * Configuration for resource set. A resource set is bundle of one or more
   * resources which can be translated to a rendering job. Each resource
   * contains a template file path, one or more value files and an output file
   * path.
   */
  @Parameter(required = true)
  private final List<ResourceBean> resourceSet = Collections.emptyList();

  /**
   * Stores maven project properties as flattened keys in case if it is required
   * to be added on jinja context for one or more resource.
   */
  private final Map<String, Object> mavenProperties = Maps.newHashMap();

  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * Entry point to rendering logic. The whole process can be optionally skipped
   * if required using the `Skip` configuration.
   *
   * <p>Step 1: Perform validation of configuration values provided by the user.
   * Step 2: For each resource bundle, render the concrete files as per the
   * provided template by substituting values from the value files. Step 3:
   * Write concrete outputs as files.
   *
   * @throws MojoExecutionException Rendering errors result in
   *     `MojoExecutionException`
   * @throws MojoFailureException Validations errors result in
   *     `MojoFailureException`
   */
  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().debug("Plugin execution begins.");

    if (Boolean.TRUE.equals(skip)) {
      getLog()
          .warn(
              String.format(
                  "jinja:%s is skipped as %s=true", DEFAULT_PLUGIN_GOAL, SKIP));
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
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

    try {
      ObjectNode configuration = mapper.createObjectNode();
      configuration.set(SKIP, BooleanNode.valueOf(skip));
      configuration.set(RESOURCE_SET, new POJONode(resourceSet));
      configuration.set(
          FAIL_ON_MISSING_VALUES, new POJONode(failOnMissingValues));
      configuration.set(OVERWRITE_OUTPUT, new POJONode(overwriteOutput));

      String jsonConfig = mapper.writeValueAsString(configuration);
      getLog().debug(String.format("Plugin Config:%n%s", jsonConfig));
    } catch (JsonProcessingException e) {
      getLog().warn("Unable to print configs", e);
    }
  }

  /**
   * Validates the configuration values provided by the user.
   *
   * @throws MojoFailureException Validations errors result in
   *     `MojoFailureException`
   */
  private void validate() throws MojoFailureException {
    getLog().debug("Starting validations.");

    validateResourceSet();
    getLog().debug("Validations complete");
  }

  /**
   * Validates the resource set values provided by the user.
   *
   * @throws MojoFailureException Validations errors result in
   *     `MojoFailureException`
   */
  private void validateResourceSet() throws MojoFailureException {
    if (resourceSet.isEmpty()) {
      throw new MojoFailureException(
          ERROR_STATEMENT,
          new IllegalArgumentException(
              String.format(
                  "'%s' must be defined with at least 1 resource.",
                  resourceSet)));
    }

    for (ResourceBean resource : resourceSet) {
      getLog().debug(String.format("Validating resource '%s'", resource));
      validateResource(resource);
    }
  }

  /**
   * Validates a resource of resource set as defined by the user.
   *
   * @param resource A user defined resource
   * @throws MojoFailureException Validations errors result in
   *     `MojoFailureException`
   */
  private void validateResource(ResourceBean resource)
      throws MojoFailureException {
    if (resource == null) {
      throw new MojoFailureException(
          ERROR_STATEMENT,
          new IllegalArgumentException(
              "Malformed 'resource' was encountered."));
    }

    validateFile("templateFilePath", resource.getTemplateFilePath());

    if (!resource.getIncludeMavenProperties()
        && resource.getValueFiles().isEmpty()) {
      throw new MojoFailureException(
          ERROR_STATEMENT,
          new IllegalArgumentException(
              "'valueFiles' must be defined with at least 1 path or "
                  + "set 'includeMavenProperties' to true."));
    }

    for (File file : resource.getValueFiles()) {
      validateFile("valueFile", file);
    }

    validateOutputFile(resource.getOutputFilePath());
    validateDependencies(resource.getDependencyDirs());
  }

  /**
   * Validates a file based on path provided by the user for a resource.
   *
   * @param key Identifier for the type of file path (templateFile or valueFile)
   * @param file Representation of file and directory path
   * @throws MojoFailureException Validations errors result in
   *     `MojoFailureException`
   */
  private void validateFile(String key, File file) throws MojoFailureException {
    if (file == null) {
      throw new MojoFailureException(
          ERROR_STATEMENT,
          new IllegalArgumentException(
              String.format("'%s' path must not be null.", key)));
    }

    if (!file.exists()) {
      throw new MojoFailureException(
          ERROR_STATEMENT,
          new IllegalArgumentException(
              String.format(
                  "Provided %s at location '%s' does not exist.", key, file)));
    }

    if (!file.isFile()) {
      throw new MojoFailureException(
          ERROR_STATEMENT,
          new IllegalArgumentException(
              String.format(
                  "Provided %s at location '%s' must be a file.", key, file)));
    }
  }

  /**
   * Validates rules are different for output files.
   *
   * @param file Representation of file and directory path
   * @throws MojoFailureException Validations errors result in
   *     `MojoFailureException`
   */
  private void validateOutputFile(File file) throws MojoFailureException {
    if (file == null) {
      throw new MojoFailureException(
          ERROR_STATEMENT,
          new IllegalArgumentException(
              "'outputFilePath' path must not be null."));
    }

    if (file.exists()) {
      if (file.isDirectory()) {
        throw new MojoFailureException(
            ERROR_STATEMENT,
            new IllegalArgumentException(
                String.format(
                    "'outputFilePath' path '%s' must be a file.", file)));
      } else if (Boolean.FALSE.equals(overwriteOutput)) {
        throw new MojoFailureException(
            ERROR_STATEMENT,
            new IllegalArgumentException(
                "Overwriting output files has been disabled in plugin config."
                    + " Set 'overwriteOutput' config to true to allow this."));
      }

      getLog()
          .warn(
              String.format(
                  "'outputFilePath' path '%s' already exists "
                      + "and will be overwritten.",
                  file));
    }
  }

  /**
   * Validates path provided as one or more dependencies location.
   *
   * @param dirs Directories to be added to FileLocator.
   * @throws MojoFailureException Validations errors result in
   *     `MojoFailureException`
   */
  private void validateDependencies(List<File> dirs)
      throws MojoFailureException {
    if (dirs == null || dirs.isEmpty()) {
      getLog().debug("No dependencies defined.");
    } else {
      for (File file : dirs) {
        if (file == null) {
          throw new MojoFailureException(
              ERROR_STATEMENT,
              new IllegalArgumentException(
                  "'dependencyPath' path must not be null."));
        }

        if (!file.exists()) {
          throw new MojoFailureException(
              ERROR_STATEMENT,
              new IllegalArgumentException(
                  String.format(
                      "Provided dependencyPath at location '%s' does not exist.",
                      file)));
        }

        if (file.isFile()) {
          throw new MojoFailureException(
              ERROR_STATEMENT,
              new IllegalArgumentException(
                  String.format(
                      "Provided dependencyPath at location '%s' must be a directory.",
                      file)));
        }
      }
    }
  }

  /**
   * Rendering logic using Jinjava.
   *
   * <p>Value file(s) are read as JSON Objects using jackson and all the nodes
   * are iterated add keys and typed values to a common context which will hold
   * all values for substitution into the template.
   *
   * <p>Reason to choose JSON format for value files: - Type safety of values -
   * Unstructured - Support complex types - Human readable and popular
   *
   * <p>Once the rendering is complete, errors are thrown if required.
   *
   * @param resource A user defined resource
   * @return Rendered content as string
   * @throws MojoExecutionException Rendering errors result in
   *     `MojoFailureException`
   */
  private String renderFromResource(ResourceBean resource)
      throws MojoExecutionException {
    JinjavaConfig jc =
        JinjavaConfig.newBuilder()
            .withFailOnUnknownTokens(failOnMissingValues)
            .build();

    Jinjava jinjava = new Jinjava(jc);
    addDependencyLocators(jinjava, resource.getDependencyDirs());

    Map<String, Object> context = Maps.newHashMap();

    try {
      String templateContent =
          FileUtils.readFileToString(
              resource.getTemplateFilePath(), StandardCharsets.UTF_8);

      // Add context from maven properties if enabled
      if (resource.getIncludeMavenProperties()) {
        addContextFromMavenProperties(context);
      }

      // Add context from provided value file(s)
      for (File valueFile : resource.getValueFiles()) {
        addContextFromValueFile(valueFile, context);
      }

      RenderResult renderResult =
          jinjava.renderForResult(templateContent, context);

      if (!renderResult.getErrors().isEmpty()
          && Boolean.TRUE.equals(failOnMissingValues)) {
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
      throw new MojoExecutionException(
          "Error occurred during resource rendering.", e);
    }
  }

  /**
   * Parses the instance of {@link MavenProject} and adds its fields (and nested
   * fields) to context while preserving their type.
   *
   * @param context jinja context of values
   * @throws JsonProcessingException this occurs when {@link MavenProject}
   *     cannot be successfully parsed to context
   */
  private void addContextFromMavenProperties(Map<String, Object> context)
      throws JsonProcessingException {
    // Process maven properties only if not done before
    if (mavenProperties.isEmpty()) {
      MavenPropertiesUtils.setMavenProperties(
          project, mavenProperties, getLog());
    }

    getLog().info("Adding maven properties to context.");
    context.putAll(mavenProperties);
  }

  /**
   * Reads the provided value file as JSON and adds nodes to context while
   * preserving their type.
   *
   * @param valueFile provided value file
   * @param context jinja context of values
   * @throws IOException this occurs in case of file reading issues
   * @throws MojoExecutionException this occurs in case of invalid keys
   */
  private void addContextFromValueFile(
      File valueFile, Map<String, Object> context)
      throws IOException, MojoExecutionException {
    Iterator<Map.Entry<String, JsonNode>> iter =
        mapper.readTree(valueFile).fields();

    while (iter.hasNext()) {
      Map.Entry<String, JsonNode> next = iter.next();
      JsonNodeType nodeType = next.getValue().getNodeType();

      if (next.getKey().contains(".")) {
        throw new MojoExecutionException(
            ERROR_STATEMENT,
            new IllegalArgumentException(
                "Keys of value files cannot contain chars in [.]"));
      }

      getLog().debug(String.format("Adding entry [ %s ] to context.", next));
      if (nodeType == JsonNodeType.ARRAY || nodeType == JsonNodeType.OBJECT) {
        context.put(next.getKey(), next.getValue());
      } else {
        context.put(next.getKey(), next.getValue().asText());
      }
    }
  }

  /**
   * Allows users locate external resource(s) like external templates to
   * include/ extend/ import from local file system.
   *
   * @param jinjava Jinja context for Java
   * @param dependencyDirs List of user provided dependency directories
   * @throws MojoExecutionException Exceptions occurred while creation of
   *     locators are wrapped as `MojoExecutionException`.
   */
  private void addDependencyLocators(Jinjava jinjava, List<File> dependencyDirs)
      throws MojoExecutionException {
    List<ResourceLocator> resourceLocatorsList = Lists.newArrayList();
    resourceLocatorsList.add(new ClasspathResourceLocator());

    for (File dir : dependencyDirs) {
      try {
        resourceLocatorsList.add(new FileLocator(dir));
      } catch (FileNotFoundException e) {
        throw new MojoExecutionException(
            "Error occurred while creating resource locator.", e);
      }
    }

    ResourceLocator[] resourceLocators =
        resourceLocatorsList.toArray(new ResourceLocator[0]);
    jinjava.setResourceLocator(new CascadingResourceLocator(resourceLocators));
  }

  /**
   * Writes the rendered content to a file.
   *
   * @param outputFile Output file as defined in some resource.
   * @param renderedContent Content to be written to file as output.
   * @throws MojoExecutionException `IOException` are recorded if any.
   */
  private void writeOutput(File outputFile, String renderedContent)
      throws MojoExecutionException {
    try {
      FileUtils.writeStringToFile(
          outputFile, renderedContent, StandardCharsets.UTF_8, false);
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Error occurred while writing output.", e);
    }
  }
}
