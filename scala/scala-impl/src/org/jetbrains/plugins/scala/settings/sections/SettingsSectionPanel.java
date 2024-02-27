package org.jetbrains.plugins.scala.settings.sections;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public abstract class SettingsSectionPanel {
    protected final Project myProject;

    protected SettingsSectionPanel(Project project) {
        myProject = project;
    }

    abstract JComponent getRootPanel();
    abstract boolean isModified();
    abstract void apply() throws ConfigurationException;
    abstract void reset();
}
