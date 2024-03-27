package org.jetbrains.plugins.scala.settings.sections;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;
import org.jetbrains.plugins.scala.settings.SimpleMappingListCellRenderer;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

import static org.jetbrains.plugins.scala.settings.ScalaProjectSettings.getInstance;

@SuppressWarnings("unchecked")
public class EditorSettingsSectionPanel extends SettingsSectionPanel {
    private JCheckBox showImplicitConversionsInCheckBox;
    private JCheckBox showArgumentsToByNameParametersCheckBox;
    private JCheckBox includeBlockExpressionsExpressionsCheckBox;
    private JCheckBox includeLiteralsCheckBox;
    private JCheckBox myAotCompletion;
    private JCheckBox useScalaClassesPriorityCheckBox;
    private JCheckBox enableConversionOnCopyCheckBox;
    private JCheckBox donTShowDialogCheckBox;
    private JCheckBox customScalatestSyntaxHighlightingCheckbox;
    private JCheckBox addOverrideToImplementCheckBox;
    private JCheckBox showNotFoundImplicitArgumentsCheckBox;
    private JComboBox<ScalaProjectSettings.ScalaCollectionHighlightingLevel> collectionHighlightingChooser;
    private JCheckBox showAmbiguousImplicitArgumentsCheckBox;
    private JComboBox<ScalaProjectSettings.AliasExportSemantics> aliasSemantics;
    private JPanel useCompilerRangesHelp;
    private JCheckBox useCompilerRanges;
    private JCheckBox showTypeMismatchHintsCheckBox;
    private JPanel rootPanel;
    private JComboBox<ScalaProjectSettings.TypeChecker> typeCheckerScala2;
    private JComboBox<ScalaProjectSettings.TypeChecker> typeCheckerScala3;
    private JLabel typeCheckerScala2Label;
    private JPanel typeCheckerScala2Help;
    private JLabel typeCheckerScala3Label;
    private JPanel typeCheckerScala3Help;
    private JPanel typeCheckerScala2Section;
    private JPanel typeCheckerScala3Section;
    private JPanel errorHighlightingSection;

    public EditorSettingsSectionPanel(Project project) {
        super(project);

        setUpTypeChecker(typeCheckerScala2, typeCheckerScala2Help);
        setUpTypeChecker(typeCheckerScala3, typeCheckerScala3Help);

        final var hasScala2 = ScalaProjectUtil.hasScala2(project);
        final var hasScala3 = ScalaProjectUtil.hasScala3(project);

        // The error highlighting section should not be visible at all in non-Scala projects.
        errorHighlightingSection.setVisible(hasScala2 || hasScala3);

        // The Scala 2: and Scala 3: labels should only be visible if both versions of the language are configured
        // in the project.
        typeCheckerScala2Label.setVisible(hasScala2 && hasScala3);
        typeCheckerScala3Label.setVisible(hasScala2 && hasScala3);

        // Only the corresponding section should be visible.
        typeCheckerScala2Section.setVisible(hasScala2);
        typeCheckerScala3Section.setVisible(hasScala3);

        enableCompilerRangesCheckbox();

        useCompilerRangesHelp.add(ContextHelpLabel.create(ScalaBundle.message("use.compiler.ranges.help")));

        aliasSemantics.setModel(new DefaultComboBoxModel<>(ScalaProjectSettings.AliasExportSemantics.values()));
        aliasSemantics.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaProjectSettings.AliasExportSemantics.Definition, ScalaBundle.message("scala.project.settings.form.alias.definition")),
                Pair.create(ScalaProjectSettings.AliasExportSemantics.Export, ScalaBundle.message("scala.project.settings.form.alias.export"))
        ));

        collectionHighlightingChooser.setModel(new DefaultComboBoxModel<>(ScalaProjectSettings.ScalaCollectionHighlightingLevel.values()));
        collectionHighlightingChooser.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaProjectSettings.ScalaCollectionHighlightingLevel.None, ScalaBundle.message("scala.collection.highlighting.type.none")),
                Pair.create(ScalaProjectSettings.ScalaCollectionHighlightingLevel.OnlyNonQualified, ScalaBundle.message("scala.collection.highlighting.type.only.non.qualified")),
                Pair.create(ScalaProjectSettings.ScalaCollectionHighlightingLevel.All, ScalaBundle.message("scala.collection.highlighting.type.all"))
        ));
    }

    private void setUpTypeChecker(JComboBox<ScalaProjectSettings.TypeChecker> typeChecker, JPanel typeCheckerHelp) {
        typeChecker.setModel(new DefaultComboBoxModel<>(ScalaProjectSettings.TypeChecker.values()));
        typeChecker.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaProjectSettings.TypeChecker.BuiltIn, ScalaBundle.message("type.checker.built.in")),
                Pair.create(ScalaProjectSettings.TypeChecker.Compiler, ScalaBundle.message("type.checker.compiler"))
        ));
        typeChecker.addActionListener(__ -> enableCompilerRangesCheckbox());
        typeCheckerHelp.add(ContextHelpLabel.create(ScalaBundle.message("type.checker.help")));
    }

    private void enableCompilerRangesCheckbox() {
        final var hasScala2 = ScalaProjectUtil.hasScala2(myProject);
        final var hasScala3 = ScalaProjectUtil.hasScala3(myProject);
        final var enabled = (hasScala2 && typeCheckerScala2.getSelectedItem() == ScalaProjectSettings.TypeChecker.Compiler) ||
                (hasScala3 && typeCheckerScala3.getSelectedItem() == ScalaProjectSettings.TypeChecker.Compiler);
        useCompilerRanges.setEnabled(enabled);
    }

    @Override
    JComponent getRootPanel() {
        return rootPanel;
    }

    @Override
    boolean isModified() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        return scalaProjectSettings.isEnableJavaToScalaConversion() != enableConversionOnCopyCheckBox.isSelected() ||
                scalaProjectSettings.isAddOverrideToImplementInConverter() != addOverrideToImplementCheckBox.isSelected() ||
                scalaProjectSettings.isDontShowConversionDialog() != donTShowDialogCheckBox.isSelected() ||

                scalaProjectSettings.isShowImplicitConversions() != showImplicitConversionsInCheckBox.isSelected() ||
                scalaProjectSettings.isTypeMismatchHints() != showTypeMismatchHintsCheckBox.isSelected() ||
                scalaProjectSettings.isShowNotFoundImplicitArguments() != showNotFoundImplicitArgumentsCheckBox.isSelected() ||
                scalaProjectSettings.isShowAmbiguousImplicitArguments() != showAmbiguousImplicitArgumentsCheckBox.isSelected() ||
                scalaProjectSettings.isShowArgumentsToByNameParams() != showArgumentsToByNameParametersCheckBox.isSelected() ||
                scalaProjectSettings.isCustomScalatestSyntaxHighlighting() != customScalatestSyntaxHighlightingCheckbox.isSelected() ||
                scalaProjectSettings.isIncludeBlockExpressions() != includeBlockExpressionsExpressionsCheckBox.isSelected() ||
                scalaProjectSettings.isIncludeLiterals() != includeLiteralsCheckBox.isSelected() ||

                scalaProjectSettings.isAotCompletion() != myAotCompletion.isSelected() ||
                scalaProjectSettings.isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected() ||
                scalaProjectSettings.getAliasSemantics() != aliasSemantics.getSelectedItem() ||
                scalaProjectSettings.getCollectionTypeHighlightingLevel() != collectionHighlightingChooser.getSelectedItem() ||

                scalaProjectSettings.isCompilerHighlightingScala2() != (typeCheckerScala2.getSelectedItem() == ScalaProjectSettings.TypeChecker.Compiler) ||
                scalaProjectSettings.isCompilerHighlightingScala3() != (typeCheckerScala3.getSelectedItem() == ScalaProjectSettings.TypeChecker.Compiler) ||
                scalaProjectSettings.isUseCompilerRanges() != useCompilerRanges.isSelected()
                ;
    }

    @Override
    void apply() throws ConfigurationException {
        if (!isModified()) return;

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        scalaProjectSettings.setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
        scalaProjectSettings.setAddOverrideToImplementInConverter(addOverrideToImplementCheckBox.isSelected());
        scalaProjectSettings.setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());

        scalaProjectSettings.setShowImplicitConversions(showImplicitConversionsInCheckBox.isSelected());
        scalaProjectSettings.setTypeMismatchHints(showTypeMismatchHintsCheckBox.isSelected());
        scalaProjectSettings.setShowNotFoundImplicitArguments(showNotFoundImplicitArgumentsCheckBox.isSelected());
        scalaProjectSettings.setShowAmbiguousImplicitArguments(showAmbiguousImplicitArgumentsCheckBox.isSelected());
        scalaProjectSettings.setShowArgumentsToByNameParams(showArgumentsToByNameParametersCheckBox.isSelected());
        if (scalaProjectSettings.isCustomScalatestSyntaxHighlighting() != customScalatestSyntaxHighlightingCheckbox.isSelected()) {
            //settings have changed. but actual highlighting will only change on next pass of highlighting visitors
            new ScalaTestHighlightingDialog().show();
        }
        scalaProjectSettings.setCustomScalatestSyntaxHighlighting(customScalatestSyntaxHighlightingCheckbox.isSelected());
        scalaProjectSettings.setIncludeBlockExpressions(includeBlockExpressionsExpressionsCheckBox.isSelected());
        scalaProjectSettings.setIncludeLiterals(includeLiteralsCheckBox.isSelected());

        scalaProjectSettings.setAotCOmpletion(myAotCompletion.isSelected());
        scalaProjectSettings.setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
        scalaProjectSettings.setAliasSemantics((ScalaProjectSettings.AliasExportSemantics) aliasSemantics.getSelectedItem());
        scalaProjectSettings.setCollectionTypeHighlightingLevel((ScalaProjectSettings.ScalaCollectionHighlightingLevel) collectionHighlightingChooser.getSelectedItem());

        scalaProjectSettings.setCompilerHighlightingScala2(typeCheckerScala2.getSelectedItem() == ScalaProjectSettings.TypeChecker.Compiler);
        scalaProjectSettings.setCompilerHighlightingScala3(typeCheckerScala3.getSelectedItem() == ScalaProjectSettings.TypeChecker.Compiler);
        scalaProjectSettings.setUseCompilerRanges(useCompilerRanges.isSelected());
    }

    @Override
    void reset() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        enableConversionOnCopyCheckBox.setSelected(scalaProjectSettings.isEnableJavaToScalaConversion());
        addOverrideToImplementCheckBox.setSelected(scalaProjectSettings.isAddOverrideToImplementInConverter());
        donTShowDialogCheckBox.setSelected(scalaProjectSettings.isDontShowConversionDialog());

        showImplicitConversionsInCheckBox.setSelected(scalaProjectSettings.isShowImplicitConversions());
        showTypeMismatchHintsCheckBox.setSelected(scalaProjectSettings.isTypeMismatchHints());
        showNotFoundImplicitArgumentsCheckBox.setSelected(scalaProjectSettings.isShowNotFoundImplicitArguments());
        showAmbiguousImplicitArgumentsCheckBox.setSelected(scalaProjectSettings.isShowAmbiguousImplicitArguments());
        showArgumentsToByNameParametersCheckBox.setSelected(scalaProjectSettings.isShowArgumentsToByNameParams());
        customScalatestSyntaxHighlightingCheckbox.setSelected(scalaProjectSettings.isCustomScalatestSyntaxHighlighting());
        includeBlockExpressionsExpressionsCheckBox.setSelected(scalaProjectSettings.isIncludeBlockExpressions());
        includeLiteralsCheckBox.setSelected(scalaProjectSettings.isIncludeLiterals());

        myAotCompletion.setSelected(scalaProjectSettings.isAotCompletion());
        useScalaClassesPriorityCheckBox.setSelected(scalaProjectSettings.isScalaPriority());
        aliasSemantics.setSelectedItem(scalaProjectSettings.getAliasSemantics());
        collectionHighlightingChooser.setSelectedItem(scalaProjectSettings.getCollectionTypeHighlightingLevel());

        typeCheckerScala2.setSelectedItem(scalaProjectSettings.isCompilerHighlightingScala2() ? ScalaProjectSettings.TypeChecker.Compiler : ScalaProjectSettings.TypeChecker.BuiltIn);
        typeCheckerScala3.setSelectedItem(scalaProjectSettings.isCompilerHighlightingScala3() ? ScalaProjectSettings.TypeChecker.Compiler : ScalaProjectSettings.TypeChecker.BuiltIn);
        useCompilerRanges.setSelected(scalaProjectSettings.isUseCompilerRanges());
        enableCompilerRangesCheckbox();
    }

    private class ScalaTestHighlightingDialog extends DialogWrapper {
        ScalaTestHighlightingDialog() {
            super(myProject);
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JComponent res = new JPanel();
            res.add(new JLabel(ScalaBundle.message("changes.in.scalatest.highlighting.will.be.processed...")));
            return res;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        protected Action[] createActions() {
            return new Action[]{getOKAction()};
        }
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
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(20, 5, new Insets(9, 9, 9, 9), -1, -1));
        final Spacer spacer1 = new Spacer();
        rootPanel.add(spacer1, new GridConstraints(19, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        showImplicitConversionsInCheckBox = new JCheckBox();
        showImplicitConversionsInCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showImplicitConversionsInCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.implicit.conversions"));
        rootPanel.add(showImplicitConversionsInCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showArgumentsToByNameParametersCheckBox = new JCheckBox();
        showArgumentsToByNameParametersCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showArgumentsToByNameParametersCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.arguments.to.by.name.parameters"));
        rootPanel.add(showArgumentsToByNameParametersCheckBox, new GridConstraints(6, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        includeBlockExpressionsExpressionsCheckBox = new JCheckBox();
        includeBlockExpressionsExpressionsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeBlockExpressionsExpressionsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.block.expressions"));
        includeBlockExpressionsExpressionsCheckBox.setToolTipText("Include expressions enclosed in in curly braces");
        rootPanel.add(includeBlockExpressionsExpressionsCheckBox, new GridConstraints(7, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        includeLiteralsCheckBox = new JCheckBox();
        includeLiteralsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeLiteralsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.literals"));
        includeLiteralsCheckBox.setToolTipText("Include string, number, etc");
        rootPanel.add(includeLiteralsCheckBox, new GridConstraints(8, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlighting"));
        rootPanel.add(titledSeparator1, new GridConstraints(1, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(102, 36), null, 0, false));
        final TitledSeparator titledSeparator2 = new TitledSeparator();
        titledSeparator2.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.autocomplete"));
        rootPanel.add(titledSeparator2, new GridConstraints(12, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myAotCompletion = new JCheckBox();
        this.$$$loadButtonText$$$(myAotCompletion, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.ahead.of.time.completion"));
        rootPanel.add(myAotCompletion, new GridConstraints(13, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useScalaClassesPriorityCheckBox = new JCheckBox();
        useScalaClassesPriorityCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(useScalaClassesPriorityCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.use.scala.classes.priority.over.java"));
        rootPanel.add(useScalaClassesPriorityCheckBox, new GridConstraints(14, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        enableConversionOnCopyCheckBox = new JCheckBox();
        enableConversionOnCopyCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(enableConversionOnCopyCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.convert.java.code.to.scala.on.copy.paste"));
        rootPanel.add(enableConversionOnCopyCheckBox, new GridConstraints(16, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        donTShowDialogCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(donTShowDialogCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.automatically.convert.to.scala.code.without.dialog"));
        rootPanel.add(donTShowDialogCheckBox, new GridConstraints(17, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator3 = new TitledSeparator();
        titledSeparator3.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.code.conversion"));
        rootPanel.add(titledSeparator3, new GridConstraints(15, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        customScalatestSyntaxHighlightingCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(customScalatestSyntaxHighlightingCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.custom.scalatest.keywords.highlighting"));
        rootPanel.add(customScalatestSyntaxHighlightingCheckbox, new GridConstraints(9, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        addOverrideToImplementCheckBox = new JCheckBox();
        addOverrideToImplementCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(addOverrideToImplementCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.add.override.keyword.to.method.implementation"));
        rootPanel.add(addOverrideToImplementCheckBox, new GridConstraints(18, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showNotFoundImplicitArgumentsCheckBox = new JCheckBox();
        showNotFoundImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showNotFoundImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.no.implicit.arguments.found"));
        rootPanel.add(showNotFoundImplicitArgumentsCheckBox, new GridConstraints(4, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel1, new GridConstraints(11, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.collection.type.highlighting.option"));
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collectionHighlightingChooser = new JComboBox();
        panel1.add(collectionHighlightingChooser, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showAmbiguousImplicitArgumentsCheckBox = new JCheckBox();
        showAmbiguousImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showAmbiguousImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.ambiguous.implicit.arguments.found"));
        rootPanel.add(showAmbiguousImplicitArgumentsCheckBox, new GridConstraints(5, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(panel2, new GridConstraints(10, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.alias.export.semantics"));
        panel2.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        aliasSemantics = new JComboBox();
        panel2.add(aliasSemantics, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showTypeMismatchHintsCheckBox = new JCheckBox();
        showTypeMismatchHintsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showTypeMismatchHintsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.type.mismatch.hints"));
        rootPanel.add(showTypeMismatchHintsCheckBox, new GridConstraints(3, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        errorHighlightingSection = new JPanel();
        errorHighlightingSection.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
        rootPanel.add(errorHighlightingSection, new GridConstraints(0, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
        final TitledSeparator titledSeparator4 = new TitledSeparator();
        titledSeparator4.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.error.highlighting"));
        errorHighlightingSection.add(titledSeparator4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        typeCheckerScala2Section = new JPanel();
        typeCheckerScala2Section.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        errorHighlightingSection.add(typeCheckerScala2Section, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        typeCheckerScala2Label = new JLabel();
        this.$$$loadLabelText$$$(typeCheckerScala2Label, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "type.checker.label.scala2"));
        typeCheckerScala2Section.add(typeCheckerScala2Label, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCheckerScala2 = new JComboBox();
        typeCheckerScala2Section.add(typeCheckerScala2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCheckerScala2Help = new JPanel();
        typeCheckerScala2Help.setLayout(new BorderLayout(0, 0));
        typeCheckerScala2Section.add(typeCheckerScala2Help, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCheckerScala3Section = new JPanel();
        typeCheckerScala3Section.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        errorHighlightingSection.add(typeCheckerScala3Section, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        typeCheckerScala3Label = new JLabel();
        this.$$$loadLabelText$$$(typeCheckerScala3Label, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "type.checker.label.scala3"));
        typeCheckerScala3Section.add(typeCheckerScala3Label, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCheckerScala3 = new JComboBox();
        typeCheckerScala3Section.add(typeCheckerScala3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCheckerScala3Help = new JPanel();
        typeCheckerScala3Help.setLayout(new BorderLayout(0, 0));
        typeCheckerScala3Section.add(typeCheckerScala3Help, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        errorHighlightingSection.add(panel3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        useCompilerRangesHelp = new JPanel();
        useCompilerRangesHelp.setLayout(new BorderLayout(0, 0));
        panel3.add(useCompilerRangesHelp, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useCompilerRanges = new JCheckBox();
        this.$$$loadButtonText$$$(useCompilerRanges, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.error.highlighting.use.compiler.ranges"));
        panel3.add(useCompilerRanges, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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

}
