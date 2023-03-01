package org.jetbrains.sbt.settings;

import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UI;
import org.jetbrains.sbt.SbtBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ResourceBundle;

@SuppressWarnings("deprecation")
public class SbtSettingsPane {
    private JRadioButton myBundledButton;
    private JRadioButton myCustomButton;
    private JTextField myMaximumHeapSize;
    private TextFieldWithBrowseButton myLauncherPath;
    private RawCommandLineEditor myVmParameters;
    private JPanel myContentPanel;
    private JrePathEditor myJrePathEditor;
    private RawCommandLineEditor mySbtOptions;
    private JPanel vmSettingsPanel;
    private JLabel sbtOptionsLabel;

    private final Project myProject;

    public SbtSettingsPane(Project project) {

        myProject = project;

        $$$setupUI$$$();

        myBundledButton.addItemListener(itemEvent -> setLauncherPathEnabled(itemEvent.getStateChange() == ItemEvent.DESELECTED));
        myBundledButton.setSelected(true);

        myCustomButton.addItemListener(itemEvent -> setLauncherPathEnabled(itemEvent.getStateChange() == ItemEvent.SELECTED));

        myLauncherPath.addBrowseFolderListener(
                SbtBundle.message("sbt.settings.choose.custom.launcher"),
                SbtBundle.message("sbt.settings.choose.sbt.launch.jar"),
                project,
                FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
        JPanel sbtOptionsLabelToolTip = UI.PanelFactory.panel(sbtOptionsLabel).withTooltip(SbtBundle.message("sbt.settings.sbtOptions.tooltip")).createPanel();
        vmSettingsPanel.add(sbtOptionsLabelToolTip, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    public void createUIComponents() {

        myJrePathEditor = new JrePathEditor(DefaultJreSelector.projectSdk(myProject));
    }

    public JPanel getContentPanel() {
        return myContentPanel;
    }

    public void setLauncherPathEnabled(boolean enabled) {
        myLauncherPath.setEnabled(enabled);
    }

    public boolean isCustomLauncher() {
        return myCustomButton.isSelected();
    }

    public boolean isCustomVM() {
        return myJrePathEditor.isAlternativeJreSelected();
    }

    public void setCustomLauncherEnabled(boolean enabled) {
        myBundledButton.setSelected(!enabled);
        myCustomButton.setSelected(enabled);
    }

    public String getLauncherPath() {
        return myLauncherPath.getText();
    }

    public String getCustomVMPath() {
        String pathOrName = myJrePathEditor.getJrePathOrName();
        return Optional.ofNullable(pathOrName)
                .flatMap(p -> Optional.ofNullable(ProjectJdkTable.getInstance().findJdk(pathOrName)))
                .map(Sdk::getHomePath)
                .orElse(pathOrName);
    }

    @SuppressWarnings("unused")
    public void setLauncherPath(String path) {
        myLauncherPath.setText(path);
    }

    @SuppressWarnings("unused")
    public void setCustomVMPath(String path, boolean useCustomVM) {
        // determine name or path based on available sdk's to maintain compatibility with old form data model
        String pathOrName = ProjectJdkTable.getInstance()
                .getSdksOfType(JavaSdk.getInstance())
                .stream()
                .filter(sdk -> StringUtil.equals(sdk.getHomePath(), path))
                .findFirst()
                .map(Sdk::getName)
                .orElse(path);
        myJrePathEditor.setPathOrName(pathOrName, useCustomVM);
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

    public String getSbtCommandArgs() {
        return mySbtOptions.getText();
    }

    public void setSbtCommandArgs(String text) {
        mySbtOptions.setText(text);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        myContentPanel = new JPanel();
        myContentPanel.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.sbtLauncher"));
        myContentPanel.add(titledSeparator1, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(panel1, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        myBundledButton = new JRadioButton();
        this.$$$loadButtonText$$$(myBundledButton, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.bundled"));
        panel1.add(myBundledButton, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCustomButton = new JRadioButton();
        this.$$$loadButtonText$$$(myCustomButton, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.custom.launcher"));
        panel1.add(myCustomButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myLauncherPath = new TextFieldWithBrowseButton();
        panel1.add(myLauncherPath, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(250, -1), null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(panel2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        panel2.add(myJrePathEditor, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(250, -1), null, null, 0, false));
        vmSettingsPanel = new JPanel();
        vmSettingsPanel.setLayout(new GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(vmSettingsPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.maxHeapSize"));
        vmSettingsPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.vmParams"));
        vmSettingsPanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myMaximumHeapSize = new JTextField();
        myMaximumHeapSize.setColumns(5);
        vmSettingsPanel.add(myMaximumHeapSize, new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(250, -1), null, 0, false));
        myVmParameters = new RawCommandLineEditor();
        myVmParameters.setDialogCaption(this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.vmParams"));
        myVmParameters.setEnabled(true);
        vmSettingsPanel.add(myVmParameters, new GridConstraints(1, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(250, -1), new Dimension(250, -1), null, 0, false));
        mySbtOptions = new RawCommandLineEditor();
        mySbtOptions.setDialogCaption(this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.sbtOptions"));
        mySbtOptions.setEnabled(true);
        vmSettingsPanel.add(mySbtOptions, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(250, -1), new Dimension(250, -1), null, 0, false));
        sbtOptionsLabel = new JLabel();
        sbtOptionsLabel.setEnabled(true);
        sbtOptionsLabel.setFocusable(true);
        this.$$$loadLabelText$$$(sbtOptionsLabel, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.sbtOptions"));
        sbtOptionsLabel.setVisible(true);
        vmSettingsPanel.add(sbtOptionsLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myContentPanel.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        myContentPanel.add(spacer2, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JToolBar.Separator toolBar$Separator1 = new JToolBar.Separator();
        myContentPanel.add(toolBar$Separator1, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label1.setLabelFor(myMaximumHeapSize);
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(myBundledButton);
        buttonGroup.add(myCustomButton);
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    private String $$$getMessageFromBundle$$$(String path, String key) {
        ResourceBundle bundle;
        try {
            Class<?> thisClass = this.getClass();
            if ($$$cachedGetBundleMethod$$$ == null) {
                Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
                $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
            }
            bundle = (ResourceBundle) $$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
        } catch (Exception e) {
            bundle = ResourceBundle.getBundle(path);
        }
        return bundle.getString(key);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myContentPanel;
    }

}
