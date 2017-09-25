package org.jetbrains.plugins.cbt.project.settings;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;

public class CbtSettingsPane {
    private TextFieldWithBrowseButton cbtPath;
    private JPanel pane;

    public void setCbtPath(String path) {
        cbtPath.setText(path);
    }

    public String getCbtPath() {
        return cbtPath.getText().trim();
    }

    public JPanel getPane() {
        return pane;
    }
}
