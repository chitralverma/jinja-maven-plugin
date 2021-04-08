package com.github.chitralverma.maven.ctp;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class ResourceBean implements Serializable {

    private File templateFilePath;
    private List<File> valueFiles;
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
        return "ResourceBean{" +
                "templateFilePath=" + templateFilePath +
                ", valueFiles=" + valueFiles +
                ", outputFilePath=" + outputFilePath +
                '}';
    }
}
