package org.jetbrains.plugins.scala.compiler.references.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.PortField;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.plugins.scala.buildinfo.BuildInfo;
import org.jetbrains.plugins.scala.compiler.CompilerIntegrationBundle;
import org.jetbrains.plugins.scala.compiler.references.ScalaCompilerReferenceService$;
import org.jetbrains.plugins.scala.compiler.references.indices.ScalaCompilerIndices;
import org.jetbrains.plugins.scala.compiler.references.package$;
import org.jetbrains.plugins.scala.settings.CompilerIndicesSbtSettings;
import org.jetbrains.plugins.scala.settings.CompilerIndicesSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

public class CompilerIndicesSettingsForm {
    private JCheckBox enableIndexingCB;
    private JButton deleteButton;
    JPanel mainPanel;
    private JBCheckBox implicitDefinitionsCB;
    private JBCheckBox applyMethodCB;
    private JBCheckBox samTypesCB;
    private JBCheckBox forCompMethodCB;
    private JRadioButton automaticConfigButton;
    private JRadioButton manualConfigButton;
    private JTextArea buildSettingsTextArea;
    private JTextArea pluginSettignsTextArea;
    private PortField portSelector;
    private JPanel manualConfigPanel;
    private JPanel findUsagesPanel;
    private JPanel compilationListenerPanel;
    private Project myProject;

    public CompilerIndicesSettingsForm(Project project) {
        this.myProject = project;
        enableIndexingCB.addItemListener(changeEvent -> updateAllPanels());
        deleteButton.setEnabled(package$.MODULE$.upToDateCompilerIndexExists(project, ScalaCompilerIndices.version()));
        deleteButton.addActionListener(e -> {
            final int answer = Messages.showYesNoDialog(project, CompilerIntegrationBundle.message("are.you.sure.you.want.to.delete.the.bytecode.indices"),
                    CompilerIntegrationBundle.message("delete.bytecode.indices"), Messages.getQuestionIcon());
            if (answer == Messages.YES) {
                invalidateIndices();
                deleteButton.setEnabled(false);
            }
        });

        updateSbtKeysTextAreaText();
        updateSbtPluginTextArea();
        portSelector.addChangeListener(e -> updateSbtKeysTextAreaText());
        manualConfigButton.addItemListener(e -> updateManualConfigurationPanel());
        pluginSettignsTextArea.setBorder(new SideBorder(JBColor.border(), SideBorder.ALL));
        buildSettingsTextArea.setBorder(new SideBorder(JBColor.border(), SideBorder.ALL));
        pluginSettignsTextArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                pluginSettignsTextArea.selectAll();
            }
        });
        buildSettingsTextArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                buildSettingsTextArea.selectAll();
            }
        });
        updateAllPanels();
    }

    private void updateAllPanels() {
        setComponentsEnabled(findUsagesPanel, enableIndexingCB.isSelected());
        setComponentsEnabled(compilationListenerPanel, enableIndexingCB.isSelected());
        updateManualConfigurationPanel();
    }

    private void updateManualConfigurationPanel() {
        setComponentsEnabled(manualConfigPanel, enableIndexingCB.isSelected() && manualConfigButton.isSelected());
    }

    private void updateSbtPluginTextArea() {
        String version = BuildInfo.sbtIdeaCompilerIndicesVersion();
        pluginSettignsTextArea.setText(
                "addSbtPlugin(\"org.jetbrains.scala\" % \"sbt-idea-compiler-indices\" % \"" + version + "\")"
        );
    }

    private void updateSbtKeysTextAreaText() {
        String messagePrefix = "ideaPort in Global :=";
        buildSettingsTextArea.setText(String.format("%s %d", messagePrefix, portSelector.getNumber()));
    }

    private void setComponentsEnabled(Container container, boolean enable) {
        Component[] components = container.getComponents();
        for (Component component : components) {
            component.setEnabled(enable);
            if (component instanceof Container) {
                setComponentsEnabled((Container) component, enable);
            }
        }
    }

    private void invalidateIndices() {
        ScalaCompilerReferenceService$.MODULE$.apply(myProject).invalidateIndex();
    }

    boolean isModified(CompilerIndicesSettings settings, CompilerIndicesSbtSettings sbtSettings) {
        boolean settingsModified =
                settings.isIndexingEnabled() != enableIndexingCB.isSelected() ||
                        settings.isEnabledForApplyUnapply() != applyMethodCB.isSelected() ||
                        settings.isEnabledForForComprehensionMethods() != forCompMethodCB.isSelected() ||
                        settings.isEnabledForImplicitDefs() != implicitDefinitionsCB.isSelected() ||
                        settings.isEnabledForSAMTypes() != samTypesCB.isSelected();

        boolean sbtSettingsModified =
                sbtSettings.useManualConfiguration() != manualConfigButton.isSelected() ||
                        sbtSettings.getSbtConnectionPort() != portSelector.getNumber();

        return settingsModified || sbtSettingsModified;
    }

    boolean applyTo(CompilerIndicesSettings settings, CompilerIndicesSbtSettings sbtSettings) {
        boolean requiresRestart =
                settings.isIndexingEnabled() != enableIndexingCB.isSelected() ||
                        sbtSettings.useManualConfiguration() != manualConfigButton.isSelected() ||
                        sbtSettings.getSbtConnectionPort() != portSelector.getNumber();

        final var oldIndexing = settings.isIndexingEnabled();
        final var newIndexing = enableIndexingCB.isSelected();
        if (oldIndexing != newIndexing) {
            invalidateIndices();
        }
        settings.setIndexingEnabled(newIndexing);
        settings.setEnabledForImplicitDefs(implicitDefinitionsCB.isSelected());
        settings.setEnabledForApplyUnapply(applyMethodCB.isSelected());
        settings.setEnabledForSAMTypes(samTypesCB.isSelected());
        settings.setEnabledForForComprehensionMethods(forCompMethodCB.isSelected());

        sbtSettings.setUseManualConfiguration(manualConfigButton.isSelected());
        sbtSettings.setSbtConnectionPort(portSelector.getNumber());

        return requiresRestart;
    }

    void from(CompilerIndicesSettings settings, CompilerIndicesSbtSettings sbtSettings) {
        enableIndexingCB.setSelected(settings.isIndexingEnabled());
        implicitDefinitionsCB.setSelected(settings.isEnabledForImplicitDefs());
        applyMethodCB.setSelected(settings.isEnabledForApplyUnapply());
        samTypesCB.setSelected(settings.isEnabledForSAMTypes());
        forCompMethodCB.setSelected(settings.isEnabledForForComprehensionMethods());
        manualConfigButton.setSelected(sbtSettings.useManualConfiguration());
        portSelector.setNumber(sbtSettings.getSbtConnectionPort());
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        enableIndexingCB = new JCheckBox();
        this.$$$loadButtonText$$$(enableIndexingCB, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "scala.compiler.indices.settings.enable.cb"));
        panel1.add(enableIndexingCB, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        deleteButton = new JButton();
        this.$$$loadButtonText$$$(deleteButton, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "scala.compiler.indices.settings.invalidate.button"));
        panel1.add(deleteButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        compilationListenerPanel = new JPanel();
        compilationListenerPanel.setLayout(new GridLayoutManager(2, 1, new Insets(6, 0, 0, 0), -1, -1));
        mainPanel.add(compilationListenerPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "sbt.compilation.listener"));
        compilationListenerPanel.add(titledSeparator1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        compilationListenerPanel.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        automaticConfigButton = new JRadioButton();
        automaticConfigButton.setSelected(true);
        this.$$$loadButtonText$$$(automaticConfigButton, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "scala.compiler.indices.sbt.automatic"));
        panel2.add(automaticConfigButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        manualConfigButton = new JRadioButton();
        this.$$$loadButtonText$$$(manualConfigButton, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "scala.compiler.indices.sbt.manual"));
        panel2.add(manualConfigButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        manualConfigPanel = new JPanel();
        manualConfigPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        manualConfigPanel.setEnabled(true);
        panel2.add(manualConfigPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 2, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "port.number"));
        manualConfigPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        portSelector = new PortField();
        portSelector.setNumber(65337);
        manualConfigPanel.add(portSelector, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 1, new Insets(3, 0, 0, 0), -1, -1));
        manualConfigPanel.add(panel3, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JBLabel jBLabel1 = new JBLabel();
        jBLabel1.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        this.$$$loadLabelText$$$(jBLabel1, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "add.line.to.plugins.sbt"));
        panel3.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pluginSettignsTextArea = new JTextArea();
        pluginSettignsTextArea.setEditable(false);
        pluginSettignsTextArea.setRows(1);
        pluginSettignsTextArea.setText("");
        panel3.add(pluginSettignsTextArea, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(150, 25), null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 1, new Insets(3, 0, 0, 0), -1, -1));
        manualConfigPanel.add(panel4, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buildSettingsTextArea = new JTextArea();
        buildSettingsTextArea.setEditable(false);
        buildSettingsTextArea.setRows(1);
        buildSettingsTextArea.setText("");
        panel4.add(buildSettingsTextArea, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(150, 25), null, 0, false));
        final JBLabel jBLabel2 = new JBLabel();
        jBLabel2.setComponentStyle(UIUtil.ComponentStyle.SMALL);
        jBLabel2.setEnabled(true);
        jBLabel2.setFontColor(UIUtil.FontColor.NORMAL);
        this.$$$loadLabelText$$$(jBLabel2, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "add.line.to.build.sbt"));
        panel4.add(jBLabel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_SOUTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(244, 14), null, 0, false));
        final Spacer spacer1 = new Spacer();
        mainPanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        findUsagesPanel = new JPanel();
        findUsagesPanel.setLayout(new GridLayoutManager(2, 1, new Insets(3, 0, 0, 0), -1, -1));
        mainPanel.add(findUsagesPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(4, 1, new Insets(1, 0, 0, 0), -1, -1));
        findUsagesPanel.add(panel5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        implicitDefinitionsCB = new JBCheckBox();
        implicitDefinitionsCB.setSelected(true);
        this.$$$loadButtonText$$$(implicitDefinitionsCB, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "implicit.definitions"));
        panel5.add(implicitDefinitionsCB, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        applyMethodCB = new JBCheckBox();
        applyMethodCB.setSelected(true);
        this.$$$loadButtonText$$$(applyMethodCB, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "apply.unapply.methods"));
        panel5.add(applyMethodCB, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        samTypesCB = new JBCheckBox();
        samTypesCB.setSelected(true);
        this.$$$loadButtonText$$$(samTypesCB, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "sam.types"));
        panel5.add(samTypesCB, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        forCompMethodCB = new JBCheckBox();
        forCompMethodCB.setSelected(true);
        this.$$$loadButtonText$$$(forCompMethodCB, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "for.comprehension.methods"));
        panel5.add(forCompMethodCB, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JBLabel jBLabel3 = new JBLabel();
        this.$$$loadLabelText$$$(jBLabel3, this.$$$getMessageFromBundle$$$("messages/CompilerIntegrationBundle", "use.indices.to.search"));
        findUsagesPanel.add(jBLabel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(manualConfigButton);
        buttonGroup.add(automaticConfigButton);
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
        return mainPanel;
    }

}
