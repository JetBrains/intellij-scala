package org.jetbrains.sbt.settings;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.ide.macro.Macro;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.distribution.DistributionComboBox;
import com.intellij.openapi.roots.ui.distribution.DistributionInfo;
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo;
import com.intellij.openapi.roots.ui.distribution.LocalDistributionInfo;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UI;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.sbt.SbtBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;


@SuppressWarnings("deprecation")
public class SbtSettingsPane {
    private JTextField maximumHeapSize;
    private RawCommandLineEditor vmParameters;
    private JPanel myContentPanel;
    private JrePathEditor jrePathEditor;
    private RawCommandLineEditor sbtOptions;
    private JPanel vmSettingsPanel;
    private EnvironmentVariablesComponent sbtEnvironment;
    private JLabel sbtOptionsLabel;
    private DistributionComboBox sbtLauncherChooser;
    private JLabel sbtLauncherLabel;
    private JLabel envVarLabel;
    private JLabel vmParametersLabel;
    private JLabel maximumHeapSizeLabel;

    private final Project myProject;
    private final DistributionInfo sbtLauncherBundledDistributionInfo = new SbtLauncherBundledDistributionInfo();

    public SbtSettingsPane(Project project) {

        myProject = project;

        $$$setupUI$$$();

        sbtLauncherChooser.setSpecifyLocationActionName(SbtBundle.message("sbt.settings.sbt.launcher.custom"));
        sbtLauncherChooser.addDistributionIfNotExists(sbtLauncherBundledDistributionInfo);
        addListenersToSbtLauncherChooser();


        JPanel sbtOptionsLabelTooltip = UI.PanelFactory.panel(sbtOptionsLabel).withTooltip(SbtBundle.message("sbt.settings.sbtOptions.tooltip")).createPanel();
        vmSettingsPanel.add(sbtOptionsLabelTooltip, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

        addListenersToJrePathEditor();

        //it is needed for mnemonics
        vmParametersLabel.setLabelFor(vmParameters);
        sbtOptionsLabel.setLabelFor(sbtOptions);
        envVarLabel.setLabelFor(sbtEnvironment);
        sbtLauncherLabel.setLabelFor(sbtLauncherChooser);
    }

    public static <T> Optional<T> castSafely(Object value, Class<T> expectedClass) {
        if (expectedClass.isInstance(value)) {
            return Optional.of(expectedClass.cast(value));
        } else {
            return Optional.empty();
        }
    }

    private Boolean ifSelectedSbtLauncherIsCorrect(ItemEvent itemEvent) {
        return castSafely(itemEvent.getItem(), DistributionComboBox.Item.Distribution.class)
                .flatMap(distribution -> castSafely(distribution.getInfo(), LocalDistributionInfo.class))
                .map(localDistributionInfo -> {
                    String path = localDistributionInfo.getPath();
                    return !path.isEmpty() && new File(path).isFile();
                })
                .orElse(true);
    }

    private void addDocumentFilterToJTextFieldDocument(JTextField jTextField) {
        castSafely(jTextField.getDocument(), AbstractDocument.class)
                .ifPresent(abstractDocument -> abstractDocument.setDocumentFilter(new DocumentFilter() {
                    @Override
                    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                            throws BadLocationException {
                        super.replace(fb, offset, length, text, attrs);
                        jTextField.setCaretPosition(0);
                    }
                }));
    }

    private void addListenersToSbtLauncherChooser() {
        installSbtLauncherChooserValidator();

        Component sbtLauncherEditorComponent = sbtLauncherChooser.getEditor().getEditorComponent();
        Optional<JTextField> sbtLauncherJTextField = castSafely(sbtLauncherEditorComponent, JTextField.class);
        sbtLauncherJTextField.ifPresent(this::addDocumentFilterToJTextFieldDocument);
        sbtLauncherChooser.addItemListener(listener -> sbtLauncherChooser.setToolTipText(getLauncherPath()));
    }

    private void addListenersToJrePathEditor() {
        Component editorComponent = jrePathEditor.getComponent().getEditor().getEditorComponent();
        castSafely(editorComponent, JTextField.class).ifPresent(jTextField -> {
            Document jTextFieldDocument = jTextField.getDocument();
            jTextFieldDocument.addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    jrePathEditor.getComponent().setToolTipText(getCustomVMPath());
                }
            });
            addDocumentFilterToJTextFieldDocument(jTextField);
        });
    }

    private void installSbtLauncherChooserValidator() {
        Optional.ofNullable(DialogWrapper.findInstanceFromFocus())
                .map(DialogWrapper::getDisposable)
                .ifPresent(disposable -> {
                    ComponentValidator validator = new ComponentValidator(disposable);
                    validator.installOn(sbtLauncherChooser);
                    validator.enableValidation();
                    sbtLauncherChooser.addItemListener(itemEvent -> {
                        if (!ifSelectedSbtLauncherIsCorrect(itemEvent))
                            validator.updateInfo(new ValidationInfo(SbtBundle.message("sbt.settings.sbt.launcher.error.jar.is.not.valid.file")).forComponent(sbtLauncherChooser));
                        else validator.updateInfo(null);
                    });
                });
    }

    public void createUIComponents() {
        sbtLauncherChooser = new DistributionComboBox(myProject, new SbtLauncherFileChooserInfo());
        jrePathEditor = new JrePathEditor(DefaultJreSelector.projectSdk(myProject));
    }

    public JPanel getContentPanel() {
        return myContentPanel;
    }

    public boolean isCustomLauncher() {
        return sbtLauncherChooser.getSelectedDistribution() instanceof LocalDistributionInfo;
    }

    public boolean isCustomVM() {
        return jrePathEditor.isAlternativeJreSelected();
    }

    public void setCustomLauncherEnabled(boolean enabled, String launcherPath) {
        DistributionInfo distribution = enabled
                ? new LocalDistributionInfo(launcherPath)
                : sbtLauncherBundledDistributionInfo;
        sbtLauncherChooser.setSelectedDistribution(distribution);
    }

    public String getLauncherPath() {
        DistributionInfo selectedDistributionInfo = sbtLauncherChooser.getSelectedDistribution();
        return castSafely(selectedDistributionInfo, LocalDistributionInfo.class)
                .map(LocalDistributionInfo::getPath)
                .orElse(null);
    }

    public String getCustomVMPath() {
        String pathOrName = jrePathEditor.getJrePathOrName();
        return Optional.ofNullable(pathOrName)
                .flatMap(p -> Optional.ofNullable(ProjectJdkTable.getInstance().findJdk(pathOrName)))
                .map(Sdk::getHomePath)
                .orElse(pathOrName);
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
        jrePathEditor.setPathOrName(pathOrName, useCustomVM);
    }

    public String getMaximumHeapSize() {
        return maximumHeapSize.getText();
    }

    public void setMaximumHeapSize(String value) {
        maximumHeapSize.setText(value);
    }

    public String getVmParameters() {
        return vmParameters.getText();
    }

    public void setMyVmParameters(String value) {
        vmParameters.setText(value);
    }

    public String getSbtCommandArgs() {
        return sbtOptions.getText();
    }

    public void setSbtCommandArgs(String text) {
        sbtOptions.setText(text);
    }

    public Map<String, String> getSbtEnvironment() {
        return sbtEnvironment.getEnvs();
    }

    public void setSbtEnvironment(Map<String, String> envs) {
        sbtEnvironment.setEnvs(envs);
    }

    public Boolean getSbtPassParentEnvironment() {
        return sbtEnvironment.isPassParentEnvs();
    }

    public void setSbtPassParentEnvironment(Boolean shouldPass) {
        sbtEnvironment.setPassParentEnvs(shouldPass);
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
        myContentPanel.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        myContentPanel.add(panel1, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        vmSettingsPanel = new JPanel();
        vmSettingsPanel.setLayout(new GridLayoutManager(6, 5, new Insets(0, 0, 0, 0), 18, -1));
        myContentPanel.add(vmSettingsPanel, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(450, 107), null, 1, false));
        maximumHeapSizeLabel = new JLabel();
        this.$$$loadLabelText$$$(maximumHeapSizeLabel, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.maxHeapSize"));
        vmSettingsPanel.add(maximumHeapSizeLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        vmParametersLabel = new JLabel();
        this.$$$loadLabelText$$$(vmParametersLabel, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.vmParams"));
        vmSettingsPanel.add(vmParametersLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        maximumHeapSize = new JTextField();
        maximumHeapSize.setColumns(5);
        maximumHeapSize.setMargin(new Insets(2, 6, 2, 6));
        vmSettingsPanel.add(maximumHeapSize, new GridConstraints(1, 1, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(287, -1), new Dimension(287, -1), null, 0, false));
        sbtOptions = new RawCommandLineEditor();
        sbtOptions.setDialogCaption(this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.sbtOptions"));
        sbtOptions.setEnabled(true);
        vmSettingsPanel.add(sbtOptions, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(287, -1), new Dimension(287, -1), null, 0, false));
        sbtLauncherLabel = new JLabel();
        this.$$$loadLabelText$$$(sbtLauncherLabel, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.sbtLauncher"));
        vmSettingsPanel.add(sbtLauncherLabel, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        sbtLauncherChooser.setAlignmentX(0.5f);
        sbtLauncherChooser.setAutoscrolls(true);
        vmSettingsPanel.add(sbtLauncherChooser, new GridConstraints(5, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(287, -1), new Dimension(287, -1), null, 0, false));
        vmSettingsPanel.add(jrePathEditor, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(462, -1), new Dimension(462, -1), null, 0, false));
        sbtOptionsLabel = new JLabel();
        this.$$$loadLabelText$$$(sbtOptionsLabel, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.sbtOptions"));
        vmSettingsPanel.add(sbtOptionsLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        envVarLabel = new JLabel();
        this.$$$loadLabelText$$$(envVarLabel, this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.env.variables"));
        vmSettingsPanel.add(envVarLabel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        sbtEnvironment = new EnvironmentVariablesComponent();
        sbtEnvironment.setText("");
        sbtEnvironment.setToolTipText("");
        vmSettingsPanel.add(sbtEnvironment, new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(287, -1), new Dimension(287, -1), null, 0, false));
        vmParameters = new RawCommandLineEditor();
        vmParameters.setDialogCaption(this.$$$getMessageFromBundle$$$("messages/SbtBundle", "sbt.settings.vmParams"));
        vmParameters.setEnabled(true);
        vmSettingsPanel.add(vmParameters, new GridConstraints(2, 1, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(287, -1), new Dimension(287, -1), null, 0, false));
        final Spacer spacer2 = new Spacer();
        myContentPanel.add(spacer2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        myContentPanel.add(spacer3, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JToolBar.Separator toolBar$Separator1 = new JToolBar.Separator();
        myContentPanel.add(toolBar$Separator1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        maximumHeapSizeLabel.setLabelFor(maximumHeapSize);
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
    public JComponent $$$getRootComponent$$$() {
        return myContentPanel;
    }

}

class SbtLauncherFileChooserInfo implements FileChooserInfo {
    @Nullable
    @Override
    public String getFileChooserTitle() {
        return null;
    }

    @Nullable
    @Override
    public String getFileChooserDescription() {
        return null;
    }

    @NotNull
    @Override
    public FileChooserDescriptor getFileChooserDescriptor() {
        return FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
    }

    @Nullable
    @Override
    public Function1<Macro, Boolean> getFileChooserMacroFilter() {
        return FileChooserInfo.Companion.getDIRECTORY_PATH();
    }
}

class SbtLauncherBundledDistributionInfo implements  DistributionInfo {
    @NotNull
    @Override
    public String getName() {
        return SbtBundle.message("sbt.settings.sbt.launcher.bundled");
    }

    @Nullable
    @Override
    public String getDescription() {
        return null;
    }
}
