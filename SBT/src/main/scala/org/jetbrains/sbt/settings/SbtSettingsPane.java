package org.jetbrains.sbt.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Pavel Fatin
 */
public class SbtSettingsPane {
    private JRadioButton myBundledButton;
    private JRadioButton myCustomButton;
    private JTextField myMaximumHeapSize;
    private TextFieldWithBrowseButton myLauncherPath;
    private RawCommandLineEditor myVmParameters;
    private JLabel myLauncherPathLabel;
    private JPanel myContentPanel;
    private JLabel customVMPathLabel;
    private TextFieldWithBrowseButton customVMPath;
    private JRadioButton useIDEVMButton;
    private JRadioButton useCustomVMButton;

    public SbtSettingsPane() {
        myBundledButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                setLauncherPathEnabled(itemEvent.getStateChange() == ItemEvent.DESELECTED);
            }
        });

        myCustomButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                setLauncherPathEnabled(itemEvent.getStateChange() == ItemEvent.SELECTED);
            }
        });

        myBundledButton.setSelected(true);

        myLauncherPath.addBrowseFolderListener("Choose a custom launcher", "Choose sbt-launch.jar", null,
                FileChooserDescriptorFactory.createSingleLocalFileDescriptor());

        useIDEVMButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                setCustomVMPathEnabled(itemEvent.getStateChange() == ItemEvent.DESELECTED);
            }
        });

        useCustomVMButton.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                setCustomVMPathEnabled(itemEvent.getStateChange() == ItemEvent.SELECTED);
            }
        });

        useCustomVMButton.setSelected(false);

        customVMPath.addBrowseFolderListener("Choose a custom VM", "Choose JRE home directory", null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor());
    }

    public JPanel getContentPanel() {
        return myContentPanel;
    }

    public void setLauncherPathEnabled(boolean enabled) {
        myLauncherPathLabel.setEnabled(enabled);
        myLauncherPath.setEnabled(enabled);
    }

    public void setCustomVMPathEnabled(boolean enabled) {
        customVMPathLabel.setEnabled(enabled);
        customVMPath.setEnabled(enabled);
    }

    public boolean isCustomLauncher() {
        return myCustomButton.isSelected();
    }

    public boolean isCustomVM() {
        return useCustomVMButton.isSelected();
    }

    public void setCustomLauncherEnabled(boolean enabled) {
        myBundledButton.setSelected(!enabled);
        myCustomButton.setSelected(enabled);
    }

    public void setCustomVMEnabled(boolean enabled) {
        useCustomVMButton.setSelected(enabled);
        useIDEVMButton.setSelected(!enabled);
    }

    public String getLauncherPath() {
        return myLauncherPath.getText();
    }

    public String getCustomVMPath() {
        return customVMPath.getText();
    }

    public void setLauncherPath(String path) {
        myLauncherPath.setText(path);
    }

    public void setCustomVMPath(String path) {
        customVMPath.setText(path);
    }

    public String getMaximumHeapSize() {
        return myMaximumHeapSize.getText();
    }

    public void setMaximumHeapSize(String value) {
        myMaximumHeapSize.setText(value);
    }

    public String getVmParameters() {
        return myVmParameters.getText();
    }

    public void setMyVmParameters(String value) {
        myVmParameters.setText(value);
    }

}
