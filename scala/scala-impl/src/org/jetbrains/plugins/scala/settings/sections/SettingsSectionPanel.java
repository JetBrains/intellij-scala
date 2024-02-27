package org.jetbrains.plugins.scala.settings.sections;

import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

public interface SettingsSectionPanel {
    JComponent getRootPanel();
    boolean isModified();
    void apply() throws ConfigurationException;
    void reset();
}
