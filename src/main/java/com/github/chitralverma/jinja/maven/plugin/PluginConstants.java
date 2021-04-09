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

/** Common Plugin Constants */
public class PluginConstants {

  public static String PluginGoalPrefix = "jinja";

  public static String DefaultPluginGoal = "generate";

  public static String Skip = "jinja-maven.skip";
  public static String ResourceSet = "jinja-maven.resourceSet";
  public static String FailOnMissingValues = "jinja-maven.failOnMissingValues";
  public static String OverwriteOutput = "jinja-maven.overwriteOutput";
}
