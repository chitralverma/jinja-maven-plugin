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

/** Common Plugin Constants. */
public final class PluginConstants {

  private PluginConstants() {}

  public static final String DEFAULT_PLUGIN_GOAL = "generate";

  public static final String SKIP = "jinja-maven.skip";
  public static final String RESOURCE_SET = "jinja-maven.resourceSet";
  public static final String FAIL_ON_MISSING_VALUES =
      "jinja-maven.failOnMissingValues";
  public static final String OVERWRITE_OUTPUT = "jinja-maven.overwriteOutput";

  public static final String MAVEN_PROPERTIES = "maven_properties";

  public static final String ERROR_STATEMENT =
      "Error occurred during configuration validation.";
}
