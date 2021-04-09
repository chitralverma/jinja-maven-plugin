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

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * {@link ResourceBean}
 *
 * <p>Holds deserialized definition of a resource defined in the resource set of
 * the plugin.
 */
public class ResourceBean implements Serializable {

  /** Path to a template file. This can be any text file. */
  private File templateFilePath;

  /**
   * Path(s) of one or more value files. Each value file must be a valid JSON.
   */
  private List<File> valueFiles;

  /**
   * Path to which output will be written after rendering. This path may or may
   * not exist and can be optionally overwritten.
   */
  private File outputFilePath;

  public File getTemplateFilePath() {
    return templateFilePath;
  }

  public void setTemplateFilePath(File templateFilePath) {
    this.templateFilePath = templateFilePath;
  }

  public List<File> getValueFiles() {
    return valueFiles;
  }

  public void setValueFiles(List<File> valueFiles) {
    this.valueFiles = valueFiles;
  }

  public File getOutputFilePath() {
    return outputFilePath;
  }

  public void setOutputFilePath(File outputFilePath) {
    this.outputFilePath = outputFilePath;
  }

  @Override
  public String toString() {
    return "ResourceBean{"
        + "templateFilePath="
        + templateFilePath
        + ", valueFiles="
        + valueFiles
        + ", outputFilePath="
        + outputFilePath
        + '}';
  }
}
