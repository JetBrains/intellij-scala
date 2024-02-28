package org.jetbrains.plugins.scala.settings;

import com.intellij.compiler.options.ModuleOptionsTableModel;
import com.intellij.compiler.options.ModuleTableCellRenderer;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.*;

import static org.jetbrains.plugins.scala.settings.ScalaProjectSettings.*;

// TODO: cleanup
//  1) split this panel into multiple per-tab
@SuppressWarnings(value = "unchecked")
public class ScalaProjectSettingsPanel {

    private static final String LAST_SELECTED_TAB_INDEX = "scala_project_settings_configurable.last_selected_tab_index";

    private JPanel myPanel;
    private JComboBox<TypeChecker> typeChecker;
    private JCheckBox useCompilerRanges;
    private JPanel typeCheckerHelp;
    private JCheckBox searchAllSymbolsIncludeCheckBox;
    private JCheckBox enableConversionOnCopyCheckBox;
    private JCheckBox donTShowDialogCheckBox;
    private JCheckBox showImplicitConversionsInCheckBox;
    private JCheckBox showArgumentsToByNameParametersCheckBox;
    private JCheckBox includeBlockExpressionsExpressionsCheckBox;
    private JCheckBox includeLiteralsCheckBox;
    private JCheckBox treatDocCommentAsBlockComment;
    private JCheckBox myDisableLanguageInjection;
    private JCheckBox useScalaClassesPriorityCheckBox;
    private JComboBox aliasSemantics;
    private JComboBox<ScalaCollectionHighlightingLevel> collectionHighlightingChooser;
    private JSpinner implicitParametersSearchDepthSpinner;
    private JCheckBox myDontCacheCompound;
    private JCheckBox myAotCompletion;
    private JCheckBox customScalatestSyntaxHighlightingCheckbox;
    private JCheckBox addOverrideToImplementCheckBox;
    private JCheckBox myProjectViewHighlighting;
    private JComboBox<ScalaMetaMode> scalaMetaMode;
    private JCheckBox metaTrimBodies;
    private JCheckBox showNotFoundImplicitArgumentsCheckBox;
    private JCheckBox showAmbiguousImplicitArgumentsCheckBox;
    private JCheckBox myGroupPackageObjectWithPackage;
    private JComboBox<Ivy2IndexingMode> ivy2IndexingModeCBB;
    private final Project myProject;

    private JPanel useCompilerRangesHelp;

    // IMPORTANT: So, we use tabbedPane for the form editor.
    //            Unfortunately the normal JTabbedPane does not really support the intellij search.
    //            That only works in TabbedPaneWrapper.
    //            The solution: We replace the JTabbedPane, created by the form code,
    //            with a TabbedPaneWrapper and move all the tabs to it.
    @Nullable
    private JTabbedPane tabbedPane;
    private JCheckBox myDoublePressAndHoldCheckbox;
    private JCheckBox myPressAndHoldCheckbox;
    private JCheckBox myShowParameterHintsCheckbox;
    private JCheckBox myShowArgumentHintsCheckbox;
    private JCheckBox myShowTypeHintsCheckbox;
    private JCheckBox myShowMethodResultsCheckbox;
    private JCheckBox myShowMemberVariablesCheckbox;
    private JCheckBox myShowLocalVariablesCheckbox;
    private JCheckBox myShowLambdaParametersCheckbox;
    private JCheckBox myShowLambdaPlaceholdersCheckbox;
    private JCheckBox myShowVariablePatternsCheckbox;
    private JCheckBox myShowMethodChainHintsCheckbox;
    private JCheckBox myShowImplicitHintsCheckbox;
    private JCheckBox myShowIndentGuidesCheckbox;
    private JCheckBox myShowMethodSeparatorsCheckbox;
    private JComboBox<XRayWidgetMode> myWidgetModeCombobox;
    private JCheckBox showTypeMismatchHintsCheckBox;
    private final TabbedPaneWrapper wrappedTabbedPane;
    private final Disposable wrappedTabbedPaneDisposer = Disposer.newDisposable();


    public ScalaProjectSettingsPanel(Project project) {
        myProject = project;

        $$$setupUI$$$();

        // Create a TabbedPaneWrapper and copy tabs from tabbedPane
        wrappedTabbedPane = new TabbedPaneWrapper(wrappedTabbedPaneDisposer);
        while (tabbedPane.getTabCount() > 0) {
            final String title = tabbedPane.getTitleAt(0);
            final Component component = tabbedPane.getComponentAt(0);
            final JComponent jcomponent = (JComponent) component;
            wrappedTabbedPane.addTab(title, jcomponent);
        }
        // make sure myPanel looks like we expect
        assert myPanel.getComponentCount() == 1;
        assert myPanel.getComponent(0) == tabbedPane;

        // Replace the tabbedPane with our new wrapped tabbedPane
        final GridLayoutManager layout = (GridLayoutManager) myPanel.getLayout();
        final GridConstraints constraints = layout.getConstraintsForComponent(tabbedPane);
        myPanel.remove(0);
        myPanel.add(wrappedTabbedPane.getComponent(), constraints);

        // set tabbedPane to null, so that using it fails
        //noinspection BoundFieldAssignment
        tabbedPane = null;


        typeChecker.setModel(new DefaultComboBoxModel<>(TypeChecker.values()));
        typeChecker.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(TypeChecker.BuiltIn, ScalaBundle.message("type.checker.built.in")),
                Pair.create(TypeChecker.Compiler, ScalaBundle.message("type.checker.compiler"))
        ));

        typeCheckerHelp.add(ContextHelpLabel.create(ScalaBundle.message("type.checker.help")));

        useCompilerRangesHelp.add(ContextHelpLabel.create(ScalaBundle.message("use.compiler.ranges.help")));

        aliasSemantics.setModel(new DefaultComboBoxModel<>(AliasExportSemantics.values()));
        aliasSemantics.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(AliasExportSemantics.Definition, ScalaBundle.message("scala.project.settings.form.alias.definition")),
                Pair.create(AliasExportSemantics.Export, ScalaBundle.message("scala.project.settings.form.alias.export"))
        ));

        collectionHighlightingChooser.setModel(new DefaultComboBoxModel<>(ScalaCollectionHighlightingLevel.values()));
        collectionHighlightingChooser.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaCollectionHighlightingLevel.None, ScalaBundle.message("scala.collection.highlighting.type.none")),
                Pair.create(ScalaCollectionHighlightingLevel.OnlyNonQualified, ScalaBundle.message("scala.collection.highlighting.type.only.non.qualified")),
                Pair.create(ScalaCollectionHighlightingLevel.All, ScalaBundle.message("scala.collection.highlighting.type.all"))
        ));

        scalaMetaMode.setModel(new EnumComboBoxModel<>(ScalaMetaMode.class));
        scalaMetaMode.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaMetaMode.Enabled, ScalaBundle.message("scala.meta.mode.enabled")),
                Pair.create(ScalaMetaMode.Disabled, ScalaBundle.message("scala.meta.mode.disabled")),
                Pair.create(ScalaMetaMode.Manual, ScalaBundle.message("scala.meta.mode.manual"))
        ));

        ivy2IndexingModeCBB.setModel(new EnumComboBoxModel<>(Ivy2IndexingMode.class));
        ivy2IndexingModeCBB.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(Ivy2IndexingMode.Disabled, ScalaBundle.message("ivy2.indexing.mode.disabled")),
                Pair.create(Ivy2IndexingMode.Metadata, ScalaBundle.message("ivy2.indexing.mode.metadata")),
                Pair.create(Ivy2IndexingMode.Classes, ScalaBundle.message("ivy2.indexing.mode.classes"))
        ));

        if (SystemInfo.isMac) {
            myDoublePressAndHoldCheckbox.setText(myDoublePressAndHoldCheckbox.getText().replace("Ctrl", "Cmd"));
            myPressAndHoldCheckbox.setText(myPressAndHoldCheckbox.getText().replace("Ctrl", "Cmd"));
        }

        Map<XRayWidgetMode, String> modes = new HashMap<>();
        modes.put(XRayWidgetMode.ALWAYS, "Always");
        modes.put(XRayWidgetMode.WHEN_ACTIVE, "When active");
        modes.put(XRayWidgetMode.NEVER, "Never");
        myWidgetModeCombobox.setModel(new DefaultComboBoxModel<>(XRayWidgetMode.values()));
        myWidgetModeCombobox.setRenderer(new SimpleMappingListCellRenderer<>(modes));
        myShowTypeHintsCheckbox.addItemListener(e -> updateTypeHintCheckboxes());

        setSettings();
        updateTypeHintCheckboxes();

        initSelectedTab();
    }

    private void updateTypeHintCheckboxes() {
        boolean b = myShowTypeHintsCheckbox.isSelected();
        myShowMemberVariablesCheckbox.setEnabled(b);
        myShowLocalVariablesCheckbox.setEnabled(b);
        myShowMethodResultsCheckbox.setEnabled(b);
        myShowMethodResultsCheckbox.setEnabled(b);
        myShowLambdaParametersCheckbox.setEnabled(b);
        myShowLambdaPlaceholdersCheckbox.setEnabled(b);
        myShowVariablePatternsCheckbox.setEnabled(b);
    }

    @NotNull
    protected FileType getFileType() {
        return ScalaFileType.INSTANCE;
    }

    public void selectXRayModeTab() {
        wrappedTabbedPane.setSelectedTitle(ScalaBundle.message("scala.project.settings.form.tabs.xray.mode"));
    }

    public void selectUpdatesTab() {
        wrappedTabbedPane.setSelectedTitle(ScalaBundle.message("scala.project.settings.form.tabs.updates"));
    }

    public void apply() throws ConfigurationException {
        if (!isModified()) return;

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        scalaProjectSettings.setImplicitParametersSearchDepth((Integer) implicitParametersSearchDepthSpinner.getValue());

        scalaProjectSettings.setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
        scalaProjectSettings.setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
        scalaProjectSettings.setAddOverrideToImplementInConverter(addOverrideToImplementCheckBox.isSelected());
        scalaProjectSettings.setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());
        scalaProjectSettings.setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());

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

        scalaProjectSettings.setDisableLangInjection(myDisableLanguageInjection.isSelected());
        scalaProjectSettings.setDontCacheCompoundTypes(myDontCacheCompound.isSelected());
        scalaProjectSettings.setAotCOmpletion(myAotCompletion.isSelected());
        scalaProjectSettings.setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
        scalaProjectSettings.setAliasSemantics((AliasExportSemantics) aliasSemantics.getSelectedItem());
        scalaProjectSettings.setCollectionTypeHighlightingLevel((ScalaCollectionHighlightingLevel) collectionHighlightingChooser.getSelectedItem());
        scalaProjectSettings.setCompilerHighlightingScala2(typeChecker.getSelectedItem() == TypeChecker.Compiler);
        scalaProjectSettings.setUseCompilerRanges(useCompilerRanges.isSelected());

        if (scalaProjectSettings.isProjectViewHighlighting() && !myProjectViewHighlighting.isSelected()) {
            ProblemSolverUtils.clearProblemsIn(myProject);
        }
        scalaProjectSettings.setProjectViewHighlighting(myProjectViewHighlighting.isSelected());
        if (scalaProjectSettings.isGroupPackageObjectWithPackage() != myGroupPackageObjectWithPackage.isSelected()) {
            AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getCurrentProjectViewPane();
            if (pane != null) {
                pane.updateFromRoot(true);
            }
        }
        scalaProjectSettings.setGroupPackageObjectWithPackage(myGroupPackageObjectWithPackage.isSelected());

        scalaProjectSettings.setScalaMetaMode((ScalaMetaMode) scalaMetaMode.getModel().getSelectedItem());
        scalaProjectSettings.setMetaTrimMethodBodies(metaTrimBodies.isSelected());

        scalaProjectSettings.setIvy2IndexingMode((Ivy2IndexingMode) ivy2IndexingModeCBB.getModel().getSelectedItem());

        ScalaApplicationSettings scalaApplicationSettings = ScalaApplicationSettings.getInstance();
        scalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD = myDoublePressAndHoldCheckbox.isSelected();
        scalaApplicationSettings.XRAY_PRESS_AND_HOLD = myPressAndHoldCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_PARAMETER_HINTS = myShowParameterHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS = myShowArgumentHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_TYPE_HINTS = myShowTypeHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_MEMBER_VARIABLE_HINTS = myShowMemberVariablesCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_LOCAL_VARIABLE_HINTS = myShowLocalVariablesCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_METHOD_RESULT_HINTS = myShowMethodResultsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_LAMBDA_PARAMETER_HINTS = myShowLambdaParametersCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS = myShowLambdaPlaceholdersCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_VARIABLE_PATTERN_HINTS = myShowVariablePatternsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_METHOD_CHAIN_HINTS = myShowMethodChainHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS = myShowImplicitHintsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES = myShowIndentGuidesCheckbox.isSelected();
        scalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS = myShowMethodSeparatorsCheckbox.isSelected();
        scalaApplicationSettings.XRAY_WIDGET_MODE = (XRayWidgetMode) myWidgetModeCombobox.getSelectedItem();
    }

    @SuppressWarnings({"RedundantIfStatement"})
    public boolean isModified() {

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        if (scalaProjectSettings.isShowImplicitConversions() !=
                showImplicitConversionsInCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isTypeMismatchHints() !=
                showTypeMismatchHintsCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isShowNotFoundImplicitArguments() !=
                showNotFoundImplicitArgumentsCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isShowAmbiguousImplicitArguments() !=
                showAmbiguousImplicitArgumentsCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isShowArgumentsToByNameParams() !=
                showArgumentsToByNameParametersCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isCustomScalatestSyntaxHighlighting() !=
                customScalatestSyntaxHighlightingCheckbox.isSelected()) return true;
        if (scalaProjectSettings.isIncludeBlockExpressions() !=
                includeBlockExpressionsExpressionsCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isIncludeLiterals() !=
                includeLiteralsCheckBox.isSelected()) return true;

        if (scalaProjectSettings.getImplicitParametersSearchDepth() !=
                (Integer) implicitParametersSearchDepthSpinner.getValue()) return true;

        if (scalaProjectSettings.isSearchAllSymbols() !=
                searchAllSymbolsIncludeCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isEnableJavaToScalaConversion() !=
                enableConversionOnCopyCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isAddOverrideToImplementInConverter() !=
                addOverrideToImplementCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isDontShowConversionDialog() !=
                donTShowDialogCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isTreatDocCommentAsBlockComment() !=
                treatDocCommentAsBlockComment.isSelected()) return true;

        if (scalaProjectSettings.isDisableLangInjection() != myDisableLanguageInjection.isSelected())
            return true;


        if (scalaProjectSettings.isDontCacheCompoundTypes() != myDontCacheCompound.isSelected()) return true;

        if (scalaProjectSettings.isAotCompletion() != myAotCompletion.isSelected())
            return true;

        if (scalaProjectSettings.isProjectViewHighlighting() != myProjectViewHighlighting.isSelected())
            return true;

        if (scalaProjectSettings.isGroupPackageObjectWithPackage() != myGroupPackageObjectWithPackage.isSelected())
            return true;

        if (scalaProjectSettings.isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected())
            return true;

        if (scalaProjectSettings.getAliasSemantics() !=
                aliasSemantics.getSelectedItem()) return true;

        if (scalaProjectSettings.getCollectionTypeHighlightingLevel() !=
                collectionHighlightingChooser.getSelectedItem()) return true;

        if (scalaProjectSettings.isCompilerHighlightingScala2() !=
                (typeChecker.getSelectedItem() == TypeChecker.Compiler)) return true;

        if (scalaProjectSettings.isUseCompilerRanges() != useCompilerRanges.isSelected()) return true;

        if (!scalaProjectSettings.getScalaMetaMode().equals(scalaMetaMode.getModel().getSelectedItem())) return true;
        if (scalaProjectSettings.isMetaTrimMethodBodies() != metaTrimBodies.isSelected()) return true;

        if (!scalaProjectSettings.getIvy2IndexingMode().equals(ivy2IndexingModeCBB.getModel().getSelectedItem()))
            return true;

        ScalaApplicationSettings scalaApplicationSettings = ScalaApplicationSettings.getInstance();
        if (scalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD != myDoublePressAndHoldCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_PRESS_AND_HOLD != myPressAndHoldCheckbox.isSelected()) return true;
        if (scalaApplicationSettings.XRAY_SHOW_PARAMETER_HINTS != myShowParameterHintsCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS != myShowArgumentHintsCheckbox.isSelected()) return true;
        if (scalaApplicationSettings.XRAY_SHOW_TYPE_HINTS != myShowTypeHintsCheckbox.isSelected()) return true;
        if (scalaApplicationSettings.XRAY_SHOW_MEMBER_VARIABLE_HINTS != myShowMemberVariablesCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_LOCAL_VARIABLE_HINTS != myShowLocalVariablesCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_METHOD_RESULT_HINTS != myShowMethodResultsCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_LAMBDA_PARAMETER_HINTS != myShowLambdaParametersCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS != myShowLambdaPlaceholdersCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_VARIABLE_PATTERN_HINTS != myShowVariablePatternsCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_METHOD_CHAIN_HINTS != myShowMethodChainHintsCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS != myShowImplicitHintsCheckbox.isSelected()) return true;
        if (scalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES != myShowIndentGuidesCheckbox.isSelected()) return true;
        if (scalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS != myShowMethodSeparatorsCheckbox.isSelected())
            return true;
        if (scalaApplicationSettings.XRAY_WIDGET_MODE != myWidgetModeCombobox.getSelectedItem()) return true;

        return false;
    }

    public JComponent getPanel() {
        return myPanel;
    }

    void dispose() {
        int tabIdx = wrappedTabbedPane.getSelectedIndex();
        PropertiesComponent.getInstance(myProject).setValue(LAST_SELECTED_TAB_INDEX, tabIdx, -1);
        wrappedTabbedPaneDisposer.dispose();
    }

    private void initSelectedTab() {
        int tabIdx = PropertiesComponent.getInstance(myProject).getInt(LAST_SELECTED_TAB_INDEX, -1);
        setSelectedTabIndex(tabIdx);
    }

    private void setSelectedTabIndex(int index) {
        if (0 <= index && index < wrappedTabbedPane.getTabCount()) {
            wrappedTabbedPane.setSelectedIndex(index);
        }
    }

    protected void resetImpl() {
        setSettings();
    }

    private void setSettings() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        setValue(implicitParametersSearchDepthSpinner, scalaProjectSettings.getImplicitParametersSearchDepth());

        setValue(searchAllSymbolsIncludeCheckBox, scalaProjectSettings.isSearchAllSymbols());
        setValue(enableConversionOnCopyCheckBox, scalaProjectSettings.isEnableJavaToScalaConversion());
        setValue(addOverrideToImplementCheckBox, scalaProjectSettings.isAddOverrideToImplementInConverter());
        setValue(donTShowDialogCheckBox, scalaProjectSettings.isDontShowConversionDialog());
        setValue(treatDocCommentAsBlockComment, scalaProjectSettings.isTreatDocCommentAsBlockComment());

        setValue(showImplicitConversionsInCheckBox, scalaProjectSettings.isShowImplicitConversions());
        setValue(showTypeMismatchHintsCheckBox, scalaProjectSettings.isTypeMismatchHints());
        setValue(showNotFoundImplicitArgumentsCheckBox, scalaProjectSettings.isShowNotFoundImplicitArguments());
        setValue(showAmbiguousImplicitArgumentsCheckBox, scalaProjectSettings.isShowAmbiguousImplicitArguments());
        setValue(showArgumentsToByNameParametersCheckBox, scalaProjectSettings.isShowArgumentsToByNameParams());
        setValue(customScalatestSyntaxHighlightingCheckbox, scalaProjectSettings.isCustomScalatestSyntaxHighlighting());
        setValue(includeBlockExpressionsExpressionsCheckBox, scalaProjectSettings.isIncludeBlockExpressions());
        setValue(includeLiteralsCheckBox, scalaProjectSettings.isIncludeLiterals());

        setValue(myDisableLanguageInjection, scalaProjectSettings.isDisableLangInjection());
        setValue(myDontCacheCompound, scalaProjectSettings.isDontCacheCompoundTypes());
        setValue(myAotCompletion, scalaProjectSettings.isAotCompletion());
        setValue(useScalaClassesPriorityCheckBox, scalaProjectSettings.isScalaPriority());
        aliasSemantics.setSelectedItem(scalaProjectSettings.getAliasSemantics());
        collectionHighlightingChooser.setSelectedItem(scalaProjectSettings.getCollectionTypeHighlightingLevel());

        setValue(myProjectViewHighlighting, scalaProjectSettings.isProjectViewHighlighting());
        setValue(myGroupPackageObjectWithPackage, scalaProjectSettings.isGroupPackageObjectWithPackage());
        typeChecker.setSelectedItem(scalaProjectSettings.isCompilerHighlightingScala2() ? TypeChecker.Compiler : TypeChecker.BuiltIn);
        setValue(useCompilerRanges, scalaProjectSettings.isUseCompilerRanges());

        scalaMetaMode.getModel().setSelectedItem(scalaProjectSettings.getScalaMetaMode());
        setValue(metaTrimBodies, scalaProjectSettings.isMetaTrimMethodBodies());

        ivy2IndexingModeCBB.getModel().setSelectedItem(scalaProjectSettings.getIvy2IndexingMode());

        ScalaApplicationSettings scalaApplicationSettings = ScalaApplicationSettings.getInstance();
        myDoublePressAndHoldCheckbox.setSelected(scalaApplicationSettings.XRAY_DOUBLE_PRESS_AND_HOLD);
        myPressAndHoldCheckbox.setSelected(scalaApplicationSettings.XRAY_PRESS_AND_HOLD);
        myShowParameterHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_PARAMETER_HINTS);
        myShowArgumentHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_ARGUMENT_HINTS);
        myShowTypeHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_TYPE_HINTS);
        myShowMemberVariablesCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_MEMBER_VARIABLE_HINTS);
        myShowLocalVariablesCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_LOCAL_VARIABLE_HINTS);
        myShowMethodResultsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_METHOD_RESULT_HINTS);
        myShowLambdaParametersCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_LAMBDA_PARAMETER_HINTS);
        myShowLambdaPlaceholdersCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_LAMBDA_PLACEHOLDER_HINTS);
        myShowVariablePatternsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_VARIABLE_PATTERN_HINTS);
        myShowMethodChainHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_METHOD_CHAIN_HINTS);
        myShowImplicitHintsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_IMPLICIT_HINTS);
        myShowIndentGuidesCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_INDENT_GUIDES);
        myShowMethodSeparatorsCheckbox.setSelected(scalaApplicationSettings.XRAY_SHOW_METHOD_SEPARATORS);
        myWidgetModeCombobox.setSelectedItem(scalaApplicationSettings.XRAY_WIDGET_MODE);
    }

    private static void setValue(JSpinner spinner, int value) {
        spinner.setValue(value);
    }

    private static void setValue(final JCheckBox box, final boolean value) {
        box.setSelected(value);
    }

    private static void setValue(final JTextField field, final String value) {
        field.setText(value);
    }

    private void createUIComponents() {
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane = new JTabbedPane();
        myPanel.add(tabbedPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(22, 5, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.editor"), panel1);
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(21, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        showImplicitConversionsInCheckBox = new JCheckBox();
        showImplicitConversionsInCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showImplicitConversionsInCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.implicit.conversions"));
        panel1.add(showImplicitConversionsInCheckBox, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showArgumentsToByNameParametersCheckBox = new JCheckBox();
        showArgumentsToByNameParametersCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showArgumentsToByNameParametersCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.arguments.to.by.name.parameters"));
        panel1.add(showArgumentsToByNameParametersCheckBox, new GridConstraints(8, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        includeBlockExpressionsExpressionsCheckBox = new JCheckBox();
        includeBlockExpressionsExpressionsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeBlockExpressionsExpressionsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.block.expressions"));
        includeBlockExpressionsExpressionsCheckBox.setToolTipText("Include expressions enclosed in in curly braces");
        panel1.add(includeBlockExpressionsExpressionsCheckBox, new GridConstraints(9, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        includeLiteralsCheckBox = new JCheckBox();
        includeLiteralsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeLiteralsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.literals"));
        includeLiteralsCheckBox.setToolTipText("Include string, number, etc");
        panel1.add(includeLiteralsCheckBox, new GridConstraints(10, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlighting"));
        panel1.add(titledSeparator1, new GridConstraints(3, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(102, 36), null, 0, false));
        final TitledSeparator titledSeparator2 = new TitledSeparator();
        titledSeparator2.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.autocomplete"));
        panel1.add(titledSeparator2, new GridConstraints(14, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myAotCompletion = new JCheckBox();
        this.$$$loadButtonText$$$(myAotCompletion, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.ahead.of.time.completion"));
        panel1.add(myAotCompletion, new GridConstraints(15, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useScalaClassesPriorityCheckBox = new JCheckBox();
        useScalaClassesPriorityCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(useScalaClassesPriorityCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.use.scala.classes.priority.over.java"));
        panel1.add(useScalaClassesPriorityCheckBox, new GridConstraints(16, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        enableConversionOnCopyCheckBox = new JCheckBox();
        enableConversionOnCopyCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(enableConversionOnCopyCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.convert.java.code.to.scala.on.copy.paste"));
        panel1.add(enableConversionOnCopyCheckBox, new GridConstraints(18, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        donTShowDialogCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(donTShowDialogCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.automatically.convert.to.scala.code.without.dialog"));
        panel1.add(donTShowDialogCheckBox, new GridConstraints(19, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator3 = new TitledSeparator();
        titledSeparator3.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.code.conversion"));
        panel1.add(titledSeparator3, new GridConstraints(17, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        customScalatestSyntaxHighlightingCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(customScalatestSyntaxHighlightingCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.custom.scalatest.keywords.highlighting"));
        panel1.add(customScalatestSyntaxHighlightingCheckbox, new GridConstraints(11, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        addOverrideToImplementCheckBox = new JCheckBox();
        addOverrideToImplementCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(addOverrideToImplementCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.add.override.keyword.to.method.implementation"));
        panel1.add(addOverrideToImplementCheckBox, new GridConstraints(20, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showNotFoundImplicitArgumentsCheckBox = new JCheckBox();
        showNotFoundImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showNotFoundImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.no.implicit.arguments.found"));
        panel1.add(showNotFoundImplicitArgumentsCheckBox, new GridConstraints(6, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(13, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.collection.type.highlighting.option"));
        panel2.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collectionHighlightingChooser = new JComboBox();
        panel2.add(collectionHighlightingChooser, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showAmbiguousImplicitArgumentsCheckBox = new JCheckBox();
        showAmbiguousImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showAmbiguousImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.ambiguous.implicit.arguments.found"));
        panel1.add(showAmbiguousImplicitArgumentsCheckBox, new GridConstraints(7, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new GridConstraints(12, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.alias.export.semantics"));
        panel3.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        aliasSemantics = new JComboBox();
        panel3.add(aliasSemantics, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final TitledSeparator titledSeparator4 = new TitledSeparator();
        titledSeparator4.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.error.highlighting"));
        panel1.add(titledSeparator4, new GridConstraints(0, 0, 1, 5, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        typeChecker = new JComboBox();
        panel4.add(typeChecker, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCheckerHelp = new JPanel();
        typeCheckerHelp.setLayout(new BorderLayout(0, 0));
        panel4.add(typeCheckerHelp, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel5, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        useCompilerRangesHelp = new JPanel();
        useCompilerRangesHelp.setLayout(new BorderLayout(0, 0));
        panel5.add(useCompilerRangesHelp, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useCompilerRanges = new JCheckBox();
        this.$$$loadButtonText$$$(useCompilerRanges, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.error.highlighting.use.compiler.ranges"));
        panel5.add(useCompilerRanges, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showTypeMismatchHintsCheckBox = new JCheckBox();
        showTypeMismatchHintsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showTypeMismatchHintsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.type.mismatch.hints"));
        panel1.add(showTypeMismatchHintsCheckBox, new GridConstraints(5, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(22, 2, new Insets(12, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.xray.mode"), panel6);
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.activate"));
        panel6.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel6.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel6.add(spacer3, new GridConstraints(21, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.show"));
        panel6.add(label4, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myShowMethodChainHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMethodChainHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.method.chain.hints"));
        panel6.add(myShowMethodChainHintsCheckbox, new GridConstraints(14, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowIndentGuidesCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowIndentGuidesCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.indent.guides"));
        panel6.add(myShowIndentGuidesCheckbox, new GridConstraints(16, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowMethodSeparatorsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMethodSeparatorsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.method.separators"));
        panel6.add(myShowMethodSeparatorsCheckbox, new GridConstraints(17, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowImplicitHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowImplicitHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.implicit.hints"));
        panel6.add(myShowImplicitHintsCheckbox, new GridConstraints(15, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowTypeHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowTypeHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.type.hints"));
        panel6.add(myShowTypeHintsCheckbox, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowMethodResultsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMethodResultsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.method.results"));
        panel6.add(myShowMethodResultsCheckbox, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowMemberVariablesCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowMemberVariablesCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.member.variables"));
        panel6.add(myShowMemberVariablesCheckbox, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowLocalVariablesCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowLocalVariablesCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.local.variables"));
        panel6.add(myShowLocalVariablesCheckbox, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowLambdaParametersCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowLambdaParametersCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.lambda.parameters"));
        panel6.add(myShowLambdaParametersCheckbox, new GridConstraints(11, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowLambdaPlaceholdersCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowLambdaPlaceholdersCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.lambda.placeholders"));
        panel6.add(myShowLambdaPlaceholdersCheckbox, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowVariablePatternsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowVariablePatternsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.variable.patterns"));
        panel6.add(myShowVariablePatternsCheckbox, new GridConstraints(13, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 2, false));
        myShowParameterHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowParameterHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.parameter.name.hints"));
        panel6.add(myShowParameterHintsCheckbox, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myShowArgumentHintsCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myShowArgumentHintsCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.by-name.argument.hints"));
        panel6.add(myShowArgumentHintsCheckbox, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myDoublePressAndHoldCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myDoublePressAndHoldCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.double.press.and.hold"));
        panel6.add(myDoublePressAndHoldCheckbox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myPressAndHoldCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(myPressAndHoldCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.press.and.hold"));
        panel6.add(myPressAndHoldCheckbox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label5 = new JLabel();
        label5.setText("");
        panel6.add(label5, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("");
        panel6.add(label6, new GridConstraints(18, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel6.add(panel7, new GridConstraints(20, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myWidgetModeCombobox = new JComboBox();
        panel7.add(myWidgetModeCombobox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        this.$$$loadLabelText$$$(label7, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.widget.display"));
        panel7.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final JLabel label8 = new JLabel();
        this.$$$loadLabelText$$$(label8, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.xray.widget"));
        panel6.add(label8, new GridConstraints(19, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(3, 1, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.project.view"), panel8);
        myProjectViewHighlighting = new JCheckBox();
        this.$$$loadButtonText$$$(myProjectViewHighlighting, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.nodes.with.errors"));
        panel8.add(myProjectViewHighlighting, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel8.add(spacer4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myGroupPackageObjectWithPackage = new JCheckBox();
        this.$$$loadButtonText$$$(myGroupPackageObjectWithPackage, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.group.package.object.with.package"));
        panel8.add(myGroupPackageObjectWithPackage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(9, 2, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.performance"), panel9);
        final Spacer spacer5 = new Spacer();
        panel9.add(spacer5, new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        this.$$$loadLabelText$$$(label9, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.implicit.parameters.search.depth"));
        panel9.add(label9, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        implicitParametersSearchDepthSpinner = new JSpinner();
        panel9.add(implicitParametersSearchDepthSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(50, -1), null, null, 0, false));
        treatDocCommentAsBlockComment = new JCheckBox();
        this.$$$loadButtonText$$$(treatDocCommentAsBlockComment, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.parsing.of.documentation.comments"));
        panel9.add(treatDocCommentAsBlockComment, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDisableLanguageInjection = new JCheckBox();
        this.$$$loadButtonText$$$(myDisableLanguageInjection, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.language.injection.in.scala.files"));
        panel9.add(myDisableLanguageInjection, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDontCacheCompound = new JCheckBox();
        this.$$$loadButtonText$$$(myDontCacheCompound, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.dont.cache.compound.types"));
        panel9.add(myDontCacheCompound, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        searchAllSymbolsIncludeCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(searchAllSymbolsIncludeCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.search.all.symbols"));
        panel9.add(searchAllSymbolsIncludeCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        this.$$$loadLabelText$$$(label10, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212"));
        label10.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212.tooltip"));
        panel9.add(label10, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scalaMetaMode = new JComboBox();
        scalaMetaMode.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.modeOptions.tooltip"));
        panel9.add(scalaMetaMode, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        metaTrimBodies = new JCheckBox();
        this.$$$loadButtonText$$$(metaTrimBodies, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.caption"));
        metaTrimBodies.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.tooltip"));
        panel9.add(metaTrimBodies, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ivy2IndexingModeCBB = new JComboBox();
        ivy2IndexingModeCBB.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel9.add(ivy2IndexingModeCBB, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        this.$$$loadLabelText$$$(label11, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode"));
        label11.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel9.add(label11, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label7.setLabelFor(myWidgetModeCombobox);
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
        return myPanel;
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

        @NotNull
        @Override
        protected Action[] createActions() {
            return new Action[]{getOKAction()};
        }
    }

}
