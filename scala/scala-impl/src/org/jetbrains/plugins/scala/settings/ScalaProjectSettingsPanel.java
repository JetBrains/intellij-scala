package org.jetbrains.plugins.scala.settings;

import com.intellij.compiler.options.ModuleOptionsTableModel;
import com.intellij.compiler.options.ModuleTableCellRenderer;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
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
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings;
import org.jetbrains.plugins.scala.components.InvalidRepoException;
import org.jetbrains.plugins.scala.components.ScalaPluginUpdater;
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier;
import org.jetbrains.plugins.scala.components.libextensions.ui.LibExtensionsSettingsPanelWrapper;
import org.jetbrains.plugins.scala.settings.uiControls.DependencyAwareInjectionSettings;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.pluginBranch;
import static org.jetbrains.plugins.scala.settings.ScalaProjectSettings.*;
import static org.jetbrains.plugins.scala.settings.uiControls.DependencyAwareInjectionSettings.ComponentWithSettings;
import static org.jetbrains.plugins.scala.settings.uiControls.DependencyAwareInjectionSettings.EP_NAME;

// TODO: cleanup
//  1) split this panel into multiple per-tab
@SuppressWarnings(value = "unchecked")
public class ScalaProjectSettingsPanel {

    private static final String LAST_SELECTED_TAB_INDEX = "scala_project_settings_configurable.last_selected_tab_index";

    private static final int WORKSHEET_RUN_DELAY_MS_MINIMUM = 500;
    private static final int WORKSHEET_RUN_DELAY_MS_MAXIMUM = 5000;
    private static final int WORKSHEET_RUN_DELAY_SPINNER_STEP_SIZE = 10;

    private final LibExtensionsSettingsPanelWrapper extensionsPanel;

    private JPanel myPanel;
    private JComboBox<TypeChecker> typeChecker;
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
    private JPanel injectionJPanel;
    private JSpinner outputSpinner;
    private JSpinner implicitParametersSearchDepthSpinner;
    private JCheckBox myDontCacheCompound;
    private JCheckBox runWorksheetInTheCheckBox;
    private JRadioButton myInheritBasePackagesRadioButton;
    private JRadioButton myUseCustomBasePackagesRadioButton;
    private JPanel myBasePackagesPanel;
    private JPanel myBasePackagesHelpPanel;
    private JBTable myBasePackagesTable;
    private JCheckBox showTypeInfoOnCheckBox;
    private JSpinner showTypeTooltipDelaySpinner;
    private JComboBox<pluginBranch> updateChannel;
    private JCheckBox myAotCompletion;
    private JCheckBox useEclipseCompatibilityModeCheckBox;
    private JTextField scalaTestDefaultSuperClass;
    private JCheckBox treatScalaScratchFilesCheckBox;
    private JSpinner worksheetAutoRunDelaySpinner;
    private JCheckBox customScalatestSyntaxHighlightingCheckbox;
    private JPanel librariesPanel;
    private JButton updateNowButton;
    private JCheckBox addOverrideToImplementCheckBox;
    private JCheckBox myProjectViewHighlighting;
    private JComboBox<ScalaMetaMode> scalaMetaMode;
    private JCheckBox metaTrimBodies;
    private JComboBox<ScFileMode> scTypeSelectionCombobox;
    private JComboBox<TrailingCommasMode> trailingCommasComboBox;
    private JCheckBox collapseWorksheetFoldByCheckBox;
    private JCheckBox showNotFoundImplicitArgumentsCheckBox;
    private JCheckBox showAmbiguousImplicitArgumentsCheckBox;
    private JCheckBox myGroupPackageObjectWithPackage;
    private JComboBox<Ivy2IndexingMode> ivy2IndexingModeCBB;
    private final Project myProject;

    private JTabbedPane tabbedPane;

    private final List<ComponentWithSettings> extraSettings = new ArrayList<>();

    public ScalaProjectSettingsPanel(Project project) {
        myProject = project;

        $$$setupUI$$$();

        typeChecker.setModel(new DefaultComboBoxModel<>(TypeChecker.values()));
        typeChecker.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(TypeChecker.BuiltIn, ScalaBundle.message("type.checker.built.in")),
                Pair.create(TypeChecker.Compiler, ScalaBundle.message("type.checker.compiler"))
        ));

        typeCheckerHelp.add(ContextHelpLabel.create(ScalaBundle.message("type.checker.help")));

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

        outputSpinner.setModel(spinnerModel(1, null, 1));

        worksheetAutoRunDelaySpinner.setModel(spinnerModel(
                WORKSHEET_RUN_DELAY_MS_MINIMUM,
                WORKSHEET_RUN_DELAY_MS_MAXIMUM,
                WORKSHEET_RUN_DELAY_SPINNER_STEP_SIZE
        ));

        updateChannel.setModel(new EnumComboBoxModel<>(pluginBranch.class));
        updateChannel.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(pluginBranch.Nightly, ScalaBundle.message("scala.plugin.chanel.nightly")),
                Pair.create(pluginBranch.EAP, ScalaBundle.message("scala.plugin.chanel.eap")),
                Pair.create(pluginBranch.Release, ScalaBundle.message("scala.plugin.chanel.release"))
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

        trailingCommasComboBox.setModel(new EnumComboBoxModel<>(TrailingCommasMode.class));
        trailingCommasComboBox.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(TrailingCommasMode.Enabled, ScalaBundle.message("trailing.commas.mode.enabled")),
                Pair.create(TrailingCommasMode.Disabled, ScalaBundle.message("trailing.commas.mode.disabled")),
                Pair.create(TrailingCommasMode.Auto, ScalaBundle.message("trailing.commas.mode.auto"))
        ));

        scTypeSelectionCombobox.setModel(new EnumComboBoxModel<>(ScFileMode.class));
        scTypeSelectionCombobox.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScFileMode.Worksheet, ScalaBundle.message("script.file.mode.always.worksheet")),
                Pair.create(ScFileMode.Ammonite, ScalaBundle.message("script.file.mode.always.ammonite")),
                Pair.create(ScFileMode.Auto, ScalaBundle.message("script.file.mode.ammonite.in.test.sources.otherwise.worksheet"))
        ));

        myBasePackagesHelpPanel.add(ContextHelpLabel.create(ScalaBundle.message("base.package.help")));
        myBasePackagesTable = new JBTable(new ModuleOptionsTableModel());
        myBasePackagesTable.setRowHeight(JBUIScale.scale(22));

        TableColumn moduleColumn = myBasePackagesTable.getColumnModel().getColumn(0);
        moduleColumn.setHeaderValue(JavaCompilerBundle.message("settings.module.column"));
        moduleColumn.setCellRenderer(new ModuleTableCellRenderer());
        int width = myBasePackagesTable.getFontMetrics(myBasePackagesTable.getFont()).stringWidth("Module Title") * 3;
        moduleColumn.setMinWidth(width);
        moduleColumn.setPreferredWidth(width);
        moduleColumn.setMaxWidth(width);

        TableColumn targetLevelColumn = myBasePackagesTable.getColumnModel().getColumn(1);
        targetLevelColumn.setHeaderValue(ScalaBundle.message("scala.project.settings.form.tabs.base.package"));

        new TableSpeedSearch(myBasePackagesTable);

        JPanel tableComp = ToolbarDecorator.createDecorator(myBasePackagesTable)
                .disableUpAction()
                .disableDownAction()
                .setAddAction(b -> addBasePackageModules())
                .setRemoveAction(b -> removeSelectedBasePackageModules())
                .createPanel();
        tableComp.setPreferredSize(new Dimension(myBasePackagesTable.getWidth(), 150));
        myBasePackagesPanel.add(tableComp);

        myInheritBasePackagesRadioButton.addActionListener(actionEvent -> myBasePackagesTable.setEnabled(!myInheritBasePackagesRadioButton.isSelected()));
        myUseCustomBasePackagesRadioButton.addActionListener(actionEvent -> myBasePackagesTable.setEnabled(myUseCustomBasePackagesRadioButton.isSelected()));

        updateNowButton.addActionListener(e -> {
            try {
                pluginBranch chanel = (pluginBranch) updateChannel.getModel().getSelectedItem();
                ScalaPluginUpdater.doUpdatePluginHosts(chanel, ScalaPluginVersionVerifier.getPluginDescriptor());
                UpdateChecker.updateAndShowResult(myProject, UpdateSettings.getInstance());
            } catch (InvalidRepoException ex) {
                Messages.showErrorDialog(ex.getMessage(), ScalaBundle.message("invalid.update.channel"));
            }
        });

        extensionsPanel = new LibExtensionsSettingsPanelWrapper((JPanel) librariesPanel.getParent(), project);
        extensionsPanel.build();

        for (DependencyAwareInjectionSettings uiWithDependency : EP_NAME.getExtensionList()) {
            extraSettings.add(uiWithDependency.createComponent(injectionJPanel));
        }

        setSettings();

        initSelectedTab();
    }

    private static SpinnerNumberModel spinnerModel(Integer min, Integer max, Integer stepSize) {
        // assuming will be changed in setSettings method
        //noinspection UnnecessaryLocalVariable
        Number value = min;
        return new SpinnerNumberModel(value, min, max, stepSize);
    }

    @NotNull
    protected FileType getFileType() {
        return ScalaFileType.INSTANCE;
    }

    public void selectUpdatesTab() {
        tabbedPane.setSelectedIndex(6);
    }

    public void apply() throws ConfigurationException {
        if (!isModified()) return;

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = showTypeInfoOnCheckBox.isSelected();
        compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY = (Integer) showTypeTooltipDelaySpinner.getValue();

        try {
            ScalaPluginUpdater.doUpdatePluginHostsAndCheck((pluginBranch) updateChannel.getModel().getSelectedItem());
        } catch (InvalidRepoException e) {
            throw new ConfigurationException(e.getMessage());
        }

        scalaProjectSettings.setInheritBasePackages(myInheritBasePackagesRadioButton.isSelected());
        scalaProjectSettings.setCustomBasePackages(getCustomBasePackages());
        scalaProjectSettings.setScalaTestDefaultSuperClass(scalaTestDefaultSuperClass.getText());
        scalaProjectSettings.setImplicitParametersSearchDepth((Integer) implicitParametersSearchDepthSpinner.getValue());
        scalaProjectSettings.setOutputLimit((Integer) outputSpinner.getValue());
        scalaProjectSettings.setInProcessMode(runWorksheetInTheCheckBox.isSelected());
        scalaProjectSettings.setWorksheetFoldCollapsedByDefault(collapseWorksheetFoldByCheckBox.isSelected());
        scalaProjectSettings.setUseEclipseCompatibility(useEclipseCompatibilityModeCheckBox.isSelected());
        scalaProjectSettings.setTreatScratchFilesAsWorksheet(treatScalaScratchFilesCheckBox.isSelected());

        scalaProjectSettings.setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
        scalaProjectSettings.setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
        scalaProjectSettings.setAddOverrideToImplementInConverter(addOverrideToImplementCheckBox.isSelected());
        scalaProjectSettings.setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());
        scalaProjectSettings.setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());

        scalaProjectSettings.setShowImplicitConversions(showImplicitConversionsInCheckBox.isSelected());
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

        scalaProjectSettings.setAutoRunDelay(getWorksheetDelay());

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

        Object type = scTypeSelectionCombobox.getSelectedItem();
        if (type != null)
            scalaProjectSettings.setScFileMode(ScFileMode.valueOf(type.toString()));
        Object trailingComa = trailingCommasComboBox.getSelectedItem();
        if (trailingComa != null)
            scalaProjectSettings.setTrailingCommasMode(TrailingCommasMode.valueOf(trailingComa.toString()));

        scalaProjectSettings.setEnableLibraryExtensions(extensionsPanel.enabledCB().isSelected());

        extraSettings.forEach(s -> s.saveSettings(scalaProjectSettings));
    }

    @SuppressWarnings({"RedundantIfStatement"})
    public boolean isModified() {

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        if (compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER != showTypeInfoOnCheckBox.isSelected()) return true;
        if (compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY != (Integer) showTypeTooltipDelaySpinner.getValue())
            return true;

        if (!ScalaPluginUpdater.getScalaPluginBranch().equals(updateChannel.getModel().getSelectedItem())) return true;

        if (scalaProjectSettings.isInheritBasePackages() !=
                myInheritBasePackagesRadioButton.isSelected()) return true;
        if (!scalaProjectSettings.getCustomBasePackages().equals(
                getCustomBasePackages())) return true;
        if (!scalaProjectSettings.getScalaTestDefaultSuperClass().equals(
                scalaTestDefaultSuperClass.getText())) return true;
        if (scalaProjectSettings.isShowImplicitConversions() !=
                showImplicitConversionsInCheckBox.isSelected()) return true;
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
        if (scalaProjectSettings.getOutputLimit() !=
                (Integer) outputSpinner.getValue()) return true;
        if (scalaProjectSettings.isInProcessMode() !=
                runWorksheetInTheCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isWorksheetFoldCollapsedByDefault() !=
                collapseWorksheetFoldByCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isUseEclipseCompatibility() != useEclipseCompatibilityModeCheckBox.isSelected())
            return true;
        if (scalaProjectSettings.isTreatScratchFilesAsWorksheet() != treatScalaScratchFilesCheckBox.isSelected())
            return true;

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

        if (scalaProjectSettings.getAutoRunDelay() != getWorksheetDelay()) return true;

        for (ComponentWithSettings setting : extraSettings) {
            if (setting.isModified(scalaProjectSettings)) return true;
        }

        if (!scalaProjectSettings.getScalaMetaMode().equals(scalaMetaMode.getModel().getSelectedItem())) return true;
        if (scalaProjectSettings.isMetaTrimMethodBodies() != metaTrimBodies.isSelected()) return true;

        if (!scalaProjectSettings.getIvy2IndexingMode().equals(ivy2IndexingModeCBB.getModel().getSelectedItem()))
            return true;

        if (scalaProjectSettings.getScFileMode() != scTypeSelectionCombobox.getSelectedItem()) return true;
        if (scalaProjectSettings.getTrailingCommasMode() != trailingCommasComboBox.getSelectedItem()) return true;

        if (scalaProjectSettings.isEnableLibraryExtensions() != extensionsPanel.enabledCB().isSelected()) return true;

        return false;
    }

    public JComponent getPanel() {
        return myPanel;
    }

    void dispose() {
        int tabIdx = tabbedPane.getSelectedIndex();
        PropertiesComponent.getInstance(myProject).setValue(LAST_SELECTED_TAB_INDEX, tabIdx, -1);
    }

    private void initSelectedTab() {
        int tabIdx = PropertiesComponent.getInstance(myProject).getInt(LAST_SELECTED_TAB_INDEX, -1);
        setSelectedTabIndex(tabIdx);
    }

    private void setSelectedTabIndex(int index) {
        if (0 <= index && index < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(index);
        }
    }

    protected void resetImpl() {
        setSettings();
    }

    private void setSettings() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        setValue(showTypeInfoOnCheckBox, compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER);
        setValue(showTypeTooltipDelaySpinner, compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY);

        updateChannel.getModel().setSelectedItem(ScalaPluginUpdater.getScalaPluginBranch());

        myInheritBasePackagesRadioButton.setSelected(scalaProjectSettings.isInheritBasePackages());
        myUseCustomBasePackagesRadioButton.setSelected(!scalaProjectSettings.isInheritBasePackages());
        myBasePackagesTable.setEnabled(!scalaProjectSettings.isInheritBasePackages());
        setCustomBasePackages(scalaProjectSettings.getCustomBasePackages());

        setValue(scalaTestDefaultSuperClass, scalaProjectSettings.getScalaTestDefaultSuperClass());
        setValue(implicitParametersSearchDepthSpinner, scalaProjectSettings.getImplicitParametersSearchDepth());
        setValue(outputSpinner, scalaProjectSettings.getOutputLimit());
        setValue(runWorksheetInTheCheckBox, scalaProjectSettings.isInProcessMode());
        setValue(collapseWorksheetFoldByCheckBox, scalaProjectSettings.isWorksheetFoldCollapsedByDefault());
        setValue(useEclipseCompatibilityModeCheckBox, scalaProjectSettings.isUseEclipseCompatibility());
        setValue(treatScalaScratchFilesCheckBox, scalaProjectSettings.isTreatScratchFilesAsWorksheet());

        setValue(searchAllSymbolsIncludeCheckBox, scalaProjectSettings.isSearchAllSymbols());
        setValue(enableConversionOnCopyCheckBox, scalaProjectSettings.isEnableJavaToScalaConversion());
        setValue(addOverrideToImplementCheckBox, scalaProjectSettings.isAddOverrideToImplementInConverter());
        setValue(donTShowDialogCheckBox, scalaProjectSettings.isDontShowConversionDialog());
        setValue(treatDocCommentAsBlockComment, scalaProjectSettings.isTreatDocCommentAsBlockComment());

        setValue(showImplicitConversionsInCheckBox, scalaProjectSettings.isShowImplicitConversions());
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
        setWorksheetDelay(scalaProjectSettings.getAutoRunDelay());

        setValue(myProjectViewHighlighting, scalaProjectSettings.isProjectViewHighlighting());
        setValue(myGroupPackageObjectWithPackage, scalaProjectSettings.isGroupPackageObjectWithPackage());
        typeChecker.setSelectedItem(scalaProjectSettings.isCompilerHighlightingScala2() ? TypeChecker.Compiler : TypeChecker.BuiltIn);

        scTypeSelectionCombobox.setSelectedItem(scalaProjectSettings.getScFileMode());
        trailingCommasComboBox.setSelectedItem(scalaProjectSettings.getTrailingCommasMode());

        for (ComponentWithSettings setting : extraSettings) {
            setting.loadSettings(scalaProjectSettings);
        }

        scalaMetaMode.getModel().setSelectedItem(scalaProjectSettings.getScalaMetaMode());
        setValue(metaTrimBodies, scalaProjectSettings.isMetaTrimMethodBodies());

        ivy2IndexingModeCBB.getModel().setSelectedItem(scalaProjectSettings.getIvy2IndexingMode());

        setValue(extensionsPanel.enabledCB(), scalaProjectSettings.isEnableLibraryExtensions());
    }

    private int getWorksheetDelay() {
        return (int) worksheetAutoRunDelaySpinner.getValue();
    }

    private void setWorksheetDelay(int delay) {
        worksheetAutoRunDelaySpinner.setValue(delay);
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

    public Map<String, String> getCustomBasePackages() {
        return ((ModuleOptionsTableModel) myBasePackagesTable.getModel()).getModuleOptions();
    }

    public void setCustomBasePackages(Map<String, String> basePackages) {
        ((ModuleOptionsTableModel) myBasePackagesTable.getModel()).setModuleOptions(myProject, basePackages);
    }

    private void addBasePackageModules() {
        int i = ((ModuleOptionsTableModel) myBasePackagesTable.getModel()).addModulesToModel(myProject, myBasePackagesPanel);
        if (i != -1) {
            TableUtil.selectRows(myBasePackagesTable, new int[]{i});
            TableUtil.scrollSelectionToVisible(myBasePackagesTable);
        }
    }

    private void removeSelectedBasePackageModules() {
        if (myBasePackagesTable.getSelectedRows().length > 0) {
            TableUtil.removeSelectedItems(myBasePackagesTable);
        }
    }

    private void createUIComponents() {
        injectionJPanel = new JPanel(new GridLayout(1, 1));
        injectionJPanel.setPreferredSize(new Dimension(200, 500));
        injectionJPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));
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
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane = new JTabbedPane();
        myPanel.add(tabbedPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(20, 4, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.editor"), panel1);
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(19, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        showImplicitConversionsInCheckBox = new JCheckBox();
        showImplicitConversionsInCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showImplicitConversionsInCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.implicit.conversions"));
        panel1.add(showImplicitConversionsInCheckBox, new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showArgumentsToByNameParametersCheckBox = new JCheckBox();
        showArgumentsToByNameParametersCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showArgumentsToByNameParametersCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.arguments.to.by.name.parameters"));
        panel1.add(showArgumentsToByNameParametersCheckBox, new GridConstraints(5, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        includeBlockExpressionsExpressionsCheckBox = new JCheckBox();
        includeBlockExpressionsExpressionsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeBlockExpressionsExpressionsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.block.expressions"));
        includeBlockExpressionsExpressionsCheckBox.setToolTipText("Include expressions enclosed in in curly braces");
        panel1.add(includeBlockExpressionsExpressionsCheckBox, new GridConstraints(6, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        includeLiteralsCheckBox = new JCheckBox();
        includeLiteralsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeLiteralsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.literals"));
        includeLiteralsCheckBox.setToolTipText("Include string, number, etc");
        panel1.add(includeLiteralsCheckBox, new GridConstraints(7, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlighting"));
        panel1.add(titledSeparator1, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final TitledSeparator titledSeparator2 = new TitledSeparator();
        titledSeparator2.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.autocomplete"));
        panel1.add(titledSeparator2, new GridConstraints(12, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myAotCompletion = new JCheckBox();
        this.$$$loadButtonText$$$(myAotCompletion, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.ahead.of.time.completion"));
        panel1.add(myAotCompletion, new GridConstraints(13, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useScalaClassesPriorityCheckBox = new JCheckBox();
        useScalaClassesPriorityCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(useScalaClassesPriorityCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.use.scala.classes.priority.over.java"));
        panel1.add(useScalaClassesPriorityCheckBox, new GridConstraints(14, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        enableConversionOnCopyCheckBox = new JCheckBox();
        enableConversionOnCopyCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(enableConversionOnCopyCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.convert.java.code.to.scala.on.copy.paste"));
        panel1.add(enableConversionOnCopyCheckBox, new GridConstraints(16, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        donTShowDialogCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(donTShowDialogCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.automatically.convert.to.scala.code.without.dialog"));
        panel1.add(donTShowDialogCheckBox, new GridConstraints(17, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator3 = new TitledSeparator();
        titledSeparator3.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.code.conversion"));
        panel1.add(titledSeparator3, new GridConstraints(15, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(9, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        showTypeInfoOnCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(showTypeInfoOnCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.type.info.on.mouse.hover"));
        panel2.add(showTypeInfoOnCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showTypeTooltipDelaySpinner = new JSpinner();
        panel2.add(showTypeTooltipDelaySpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        customScalatestSyntaxHighlightingCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(customScalatestSyntaxHighlightingCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.custom.scalatest.keywords.highlighting"));
        panel1.add(customScalatestSyntaxHighlightingCheckbox, new GridConstraints(8, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        addOverrideToImplementCheckBox = new JCheckBox();
        addOverrideToImplementCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(addOverrideToImplementCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.add.override.keyword.to.method.implementation"));
        panel1.add(addOverrideToImplementCheckBox, new GridConstraints(18, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showNotFoundImplicitArgumentsCheckBox = new JCheckBox();
        showNotFoundImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showNotFoundImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.no.implicit.arguments.found"));
        panel1.add(showNotFoundImplicitArgumentsCheckBox, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new GridConstraints(11, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.collection.type.highlighting.option"));
        panel3.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collectionHighlightingChooser = new JComboBox();
        panel3.add(collectionHighlightingChooser, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showAmbiguousImplicitArgumentsCheckBox = new JCheckBox();
        showAmbiguousImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showAmbiguousImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.ambiguous.implicit.arguments.found"));
        panel1.add(showAmbiguousImplicitArgumentsCheckBox, new GridConstraints(4, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.error.highlighting"));
        panel1.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        typeChecker = new JComboBox();
        panel4.add(typeChecker, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCheckerHelp = new JPanel();
        typeCheckerHelp.setLayout(new BorderLayout(0, 0));
        panel4.add(typeCheckerHelp, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel5, new GridConstraints(10, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.alias.export.semantics"));
        panel5.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        aliasSemantics = new JComboBox();
        panel5.add(aliasSemantics, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(3, 1, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.project.view"), panel6);
        myProjectViewHighlighting = new JCheckBox();
        this.$$$loadButtonText$$$(myProjectViewHighlighting, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.nodes.with.errors"));
        panel6.add(myProjectViewHighlighting, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel6.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myGroupPackageObjectWithPackage = new JCheckBox();
        this.$$$loadButtonText$$$(myGroupPackageObjectWithPackage, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.group.package.object.with.package"));
        panel6.add(myGroupPackageObjectWithPackage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(9, 2, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.performance"), panel7);
        final Spacer spacer3 = new Spacer();
        panel7.add(spacer3, new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.implicit.parameters.search.depth"));
        panel7.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        implicitParametersSearchDepthSpinner = new JSpinner();
        panel7.add(implicitParametersSearchDepthSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(50, -1), null, null, 0, false));
        treatDocCommentAsBlockComment = new JCheckBox();
        this.$$$loadButtonText$$$(treatDocCommentAsBlockComment, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.parsing.of.documentation.comments"));
        panel7.add(treatDocCommentAsBlockComment, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDisableLanguageInjection = new JCheckBox();
        this.$$$loadButtonText$$$(myDisableLanguageInjection, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.language.injection.in.scala.files"));
        panel7.add(myDisableLanguageInjection, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDontCacheCompound = new JCheckBox();
        this.$$$loadButtonText$$$(myDontCacheCompound, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.dont.cache.compound.types"));
        panel7.add(myDontCacheCompound, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        searchAllSymbolsIncludeCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(searchAllSymbolsIncludeCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.search.all.symbols"));
        panel7.add(searchAllSymbolsIncludeCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        this.$$$loadLabelText$$$(label5, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212"));
        label5.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212.tooltip"));
        panel7.add(label5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scalaMetaMode = new JComboBox();
        scalaMetaMode.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.modeOptions.tooltip"));
        panel7.add(scalaMetaMode, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        metaTrimBodies = new JCheckBox();
        this.$$$loadButtonText$$$(metaTrimBodies, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.caption"));
        metaTrimBodies.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.tooltip"));
        panel7.add(metaTrimBodies, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ivy2IndexingModeCBB = new JComboBox();
        ivy2IndexingModeCBB.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel7.add(ivy2IndexingModeCBB, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        this.$$$loadLabelText$$$(label6, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode"));
        label6.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel7.add(label6, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(8, 4, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.worksheet"), panel8);
        final Spacer spacer4 = new Spacer();
        panel8.add(spacer4, new GridConstraints(7, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        this.$$$loadLabelText$$$(label7, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.output.cutoff.limit"));
        panel8.add(label7, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        this.$$$loadLabelText$$$(label8, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.treat.sc.files.as"));
        panel8.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scTypeSelectionCombobox = new JComboBox();
        panel8.add(scTypeSelectionCombobox, new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        this.$$$loadLabelText$$$(label9, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.delay.before.auto.run"));
        panel8.add(label9, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        outputSpinner = new JSpinner();
        panel8.add(outputSpinner, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        this.$$$loadLabelText$$$(label10, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.output.cutoff.limit.units"));
        panel8.add(label10, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel8.add(spacer5, new GridConstraints(5, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        panel8.add(spacer6, new GridConstraints(6, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label11 = new JLabel();
        this.$$$loadLabelText$$$(label11, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.delay.before.auto.run.units"));
        panel8.add(label11, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        worksheetAutoRunDelaySpinner = new JSpinner();
        panel8.add(worksheetAutoRunDelaySpinner, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        runWorksheetInTheCheckBox = new JCheckBox();
        runWorksheetInTheCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(runWorksheetInTheCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.run.worksheet.in.the.compiler.process"));
        panel8.add(runWorksheetInTheCheckBox, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useEclipseCompatibilityModeCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(useEclipseCompatibilityModeCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.use.eclipse.compatibility.mode"));
        panel8.add(useEclipseCompatibilityModeCheckBox, new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        treatScalaScratchFilesCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(treatScalaScratchFilesCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.treat.scala.scratch.files.as.worksheet.files"));
        panel8.add(treatScalaScratchFilesCheckBox, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collapseWorksheetFoldByCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(collapseWorksheetFoldByCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.collapse.long.output.by.default"));
        panel8.add(collapseWorksheetFoldByCheckBox, new GridConstraints(4, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(4, 3, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.base.packages"), panel9);
        final Spacer spacer7 = new Spacer();
        panel9.add(spacer7, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myInheritBasePackagesRadioButton = new JRadioButton();
        this.$$$loadButtonText$$$(myInheritBasePackagesRadioButton, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.base.package.inherit"));
        panel9.add(myInheritBasePackagesRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myUseCustomBasePackagesRadioButton = new JRadioButton();
        this.$$$loadButtonText$$$(myUseCustomBasePackagesRadioButton, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.base.package.custom"));
        panel9.add(myUseCustomBasePackagesRadioButton, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myBasePackagesPanel = new JPanel();
        myBasePackagesPanel.setLayout(new BorderLayout(0, 0));
        panel9.add(myBasePackagesPanel, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myBasePackagesHelpPanel = new JPanel();
        myBasePackagesHelpPanel.setLayout(new BorderLayout(0, 0));
        panel9.add(myBasePackagesHelpPanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer8 = new Spacer();
        panel9.add(spacer8, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(4, 3, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.misc"), panel10);
        panel10.add(injectionJPanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label12 = new JLabel();
        this.$$$loadLabelText$$$(label12, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scalatest.default.super.class"));
        panel10.add(label12, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer9 = new Spacer();
        panel10.add(spacer9, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scalaTestDefaultSuperClass = new JTextField();
        scalaTestDefaultSuperClass.setColumns(25);
        panel10.add(scalaTestDefaultSuperClass, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label13 = new JLabel();
        this.$$$loadLabelText$$$(label13, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.trailing.commas"));
        panel10.add(label13, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        trailingCommasComboBox = new JComboBox();
        panel10.add(trailingCommasComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer10 = new Spacer();
        panel10.add(spacer10, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridLayoutManager(4, 4, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.updates"), panel11);
        final Spacer spacer11 = new Spacer();
        panel11.add(spacer11, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label14 = new JLabel();
        this.$$$loadLabelText$$$(label14, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.plugin.update.channel"));
        panel11.add(label14, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        updateChannel = new JComboBox();
        updateChannel.setEditable(false);
        panel11.add(updateChannel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        updateNowButton = new JButton();
        this.$$$loadButtonText$$$(updateNowButton, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.check.for.updates"));
        panel11.add(updateNowButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer12 = new Spacer();
        panel11.add(spacer12, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label15 = new JLabel();
        this.$$$loadLabelText$$$(label15, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.info"));
        panel11.add(label15, new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label16 = new JLabel();
        label16.setText("");
        panel11.add(label16, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridLayoutManager(1, 1, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.extensions"), panel12);
        librariesPanel = new JPanel();
        librariesPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel12.add(librariesPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
        label2.setLabelFor(typeChecker);
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(myInheritBasePackagesRadioButton);
        buttonGroup.add(myUseCustomBasePackagesRadioButton);
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
