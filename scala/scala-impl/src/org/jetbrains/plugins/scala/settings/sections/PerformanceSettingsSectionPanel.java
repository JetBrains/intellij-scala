package org.jetbrains.plugins.scala.settings.sections;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;
import org.jetbrains.plugins.scala.settings.SimpleMappingListCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

import static org.jetbrains.plugins.scala.settings.ScalaProjectSettings.getInstance;

@SuppressWarnings("unchecked")
public class PerformanceSettingsSectionPanel extends SettingsSectionPanel {
    private JLabel myPerformanceGuidelines;
    private JSpinner implicitParametersSearchDepthSpinner;
    private JCheckBox treatDocCommentAsBlockComment;
    private JCheckBox myDisableLanguageInjection;
    private JCheckBox myDontCacheCompound;
    private JCheckBox searchAllSymbolsIncludeCheckBox;
    private JComboBox<ScalaProjectSettings.ScalaMetaMode> scalaMetaMode;
    private JCheckBox metaTrimBodies;
    private JComboBox<ScalaProjectSettings.Ivy2IndexingMode> ivy2IndexingModeCBB;
    private JPanel rootPanel;

    protected PerformanceSettingsSectionPanel(Project project) {
        super(project);

        $$$setupUI$$$();

        scalaMetaMode.setModel(new EnumComboBoxModel<>(ScalaProjectSettings.ScalaMetaMode.class));
        scalaMetaMode.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaProjectSettings.ScalaMetaMode.Enabled, ScalaBundle.message("scala.meta.mode.enabled")),
                Pair.create(ScalaProjectSettings.ScalaMetaMode.Disabled, ScalaBundle.message("scala.meta.mode.disabled")),
                Pair.create(ScalaProjectSettings.ScalaMetaMode.Manual, ScalaBundle.message("scala.meta.mode.manual"))
        ));

        ivy2IndexingModeCBB.setModel(new EnumComboBoxModel<>(ScalaProjectSettings.Ivy2IndexingMode.class));
        ivy2IndexingModeCBB.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaProjectSettings.Ivy2IndexingMode.Disabled, ScalaBundle.message("ivy2.indexing.mode.disabled")),
                Pair.create(ScalaProjectSettings.Ivy2IndexingMode.Metadata, ScalaBundle.message("ivy2.indexing.mode.metadata")),
                Pair.create(ScalaProjectSettings.Ivy2IndexingMode.Classes, ScalaBundle.message("ivy2.indexing.mode.classes"))
        ));

        reset();
    }

    @Override
    JComponent getRootPanel() {
        return rootPanel;
    }

    @Override
    boolean isModified() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        return scalaProjectSettings.getImplicitParametersSearchDepth() != getValue(implicitParametersSearchDepthSpinner) ||
                scalaProjectSettings.isSearchAllSymbols() != searchAllSymbolsIncludeCheckBox.isSelected() ||
                scalaProjectSettings.isTreatDocCommentAsBlockComment() != treatDocCommentAsBlockComment.isSelected() ||
                scalaProjectSettings.isDisableLangInjection() != myDisableLanguageInjection.isSelected() ||
                scalaProjectSettings.isDontCacheCompoundTypes() != myDontCacheCompound.isSelected() ||
                !scalaProjectSettings.getScalaMetaMode().equals(scalaMetaMode.getModel().getSelectedItem()) ||
                scalaProjectSettings.isMetaTrimMethodBodies() != metaTrimBodies.isSelected() ||
                !scalaProjectSettings.getIvy2IndexingMode().equals(ivy2IndexingModeCBB.getModel().getSelectedItem())
                ;
    }

    @Override
    void apply() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        scalaProjectSettings.setImplicitParametersSearchDepth((Integer) implicitParametersSearchDepthSpinner.getValue());
        scalaProjectSettings.setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
        scalaProjectSettings.setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());
        scalaProjectSettings.setDisableLangInjection(myDisableLanguageInjection.isSelected());
        scalaProjectSettings.setDontCacheCompoundTypes(myDontCacheCompound.isSelected());
        scalaProjectSettings.setScalaMetaMode((ScalaProjectSettings.ScalaMetaMode) scalaMetaMode.getModel().getSelectedItem());
        scalaProjectSettings.setMetaTrimMethodBodies(metaTrimBodies.isSelected());
        scalaProjectSettings.setIvy2IndexingMode((ScalaProjectSettings.Ivy2IndexingMode) ivy2IndexingModeCBB.getModel().getSelectedItem());
    }

    @Override
    void reset() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        setValue(implicitParametersSearchDepthSpinner, scalaProjectSettings.getImplicitParametersSearchDepth());
        searchAllSymbolsIncludeCheckBox.setSelected(scalaProjectSettings.isSearchAllSymbols());
        treatDocCommentAsBlockComment.setSelected(scalaProjectSettings.isTreatDocCommentAsBlockComment());
        myDisableLanguageInjection.setSelected(scalaProjectSettings.isDisableLangInjection());
        myDontCacheCompound.setSelected(scalaProjectSettings.isDontCacheCompoundTypes());
        scalaMetaMode.getModel().setSelectedItem(scalaProjectSettings.getScalaMetaMode());
        metaTrimBodies.setSelected(scalaProjectSettings.isMetaTrimMethodBodies());
        ivy2IndexingModeCBB.getModel().setSelectedItem(scalaProjectSettings.getIvy2IndexingMode());
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
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(11, 3, new Insets(9, 9, 9, 9), -1, -1));
        final Spacer spacer1 = new Spacer();
        rootPanel.add(spacer1, new GridConstraints(10, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.performance.advanced"));
        rootPanel.add(titledSeparator1, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(2, 0, 8, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.implicit.parameters.search.depth"));
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        implicitParametersSearchDepthSpinner = new JSpinner();
        panel1.add(implicitParametersSearchDepthSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(50, -1), null, null, 1, false));
        treatDocCommentAsBlockComment = new JCheckBox();
        this.$$$loadButtonText$$$(treatDocCommentAsBlockComment, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.parsing.of.documentation.comments"));
        treatDocCommentAsBlockComment.setVerticalTextPosition(0);
        panel1.add(treatDocCommentAsBlockComment, new GridConstraints(5, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myDisableLanguageInjection = new JCheckBox();
        this.$$$loadButtonText$$$(myDisableLanguageInjection, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.language.injection.in.scala.files"));
        myDisableLanguageInjection.setVerticalTextPosition(0);
        panel1.add(myDisableLanguageInjection, new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myDontCacheCompound = new JCheckBox();
        this.$$$loadButtonText$$$(myDontCacheCompound, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.dont.cache.compound.types"));
        panel1.add(myDontCacheCompound, new GridConstraints(7, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        searchAllSymbolsIncludeCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(searchAllSymbolsIncludeCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.search.all.symbols"));
        panel1.add(searchAllSymbolsIncludeCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212"));
        label2.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212.tooltip"));
        panel1.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        scalaMetaMode = new JComboBox();
        scalaMetaMode.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.modeOptions.tooltip"));
        panel1.add(scalaMetaMode, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        metaTrimBodies = new JCheckBox();
        this.$$$loadButtonText$$$(metaTrimBodies, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.caption"));
        metaTrimBodies.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.tooltip"));
        panel1.add(metaTrimBodies, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        ivy2IndexingModeCBB = new JComboBox();
        ivy2IndexingModeCBB.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel1.add(ivy2IndexingModeCBB, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode"));
        label3.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel1.add(label3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 1, false));
        this.$$$loadLabelText$$$(myPerformanceGuidelines, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.performance.guidelines"));
        rootPanel.add(myPerformanceGuidelines, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(1000, -1), null, 0, false));
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
        return rootPanel;
    }

    private void createUIComponents() {
        JBLabel label = new JBLabel();
        label.setCopyable(true);
        label.setAllowAutoWrapping(true);
        myPerformanceGuidelines = label;
    }
}
