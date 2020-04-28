package org.jetbrains.plugins.scala.testingSupport.test;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit2.configuration.JUnitConfigurable;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.execution.ui.DefaultJreSelector;
import com.intellij.execution.ui.JrePathEditor;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil;
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager;
import org.jetbrains.plugins.scala.settings.SimpleMappingListCellRenderer;
import org.jetbrains.plugins.scala.testingSupport.test.testdata.RegexpTestData;
import org.jetbrains.plugins.scala.testingSupport.test.testdata.SingleTestData;
import org.jetbrains.plugins.scala.testingSupport.test.testdata.TestConfigurationData;
import org.jetbrains.sbt.settings.SbtSettings;
import scala.Option;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

// TODO 1: convert to Scala
@SuppressWarnings(value = "unchecked")
public final class TestRunConfigurationForm {

    private JPanel myWholePanel;
    private final Project myProject;

    private List<String> suitePaths;
    private final ConfigurationModuleSelector myModuleSelector;

    // TOP PANEL: TestKind-specific
    private LabeledComponent<ComboBox<TestKind>> myTestKind;
    private LabeledComponent<EditorTextFieldWithBrowseButton> myClass;
    private LabeledComponent<MyMultilineEditorTextField> myTestName;
    private LabeledComponent<EditorTextFieldWithBrowseButton> myPackage;
    private LabeledComponent<JComboBox<SearchForTest>> mySearchForTest;
    private LabeledComponent<RegexpPanel> myRegex;
    private JCheckBox useSbtCheckBox;
    private JCheckBox useUiWithSbt;

    // BOTTOM PANEL: common options
    private CommonScalaParametersPanel myCommonScalaParameters;
    private LabeledComponent<ModuleDescriptionsComboBox> myModule;
    private JrePathEditor myJrePathEditor;
    private JCheckBox myShowProgressMessagesCheckBox;

    public TestRunConfigurationForm(final Project project) {
        myProject = project;

        createUiComponents();

        JComboBox<TestKind> testKindComponent = myTestKind.getComponent();
        testKindComponent.setModel(new EnumComboBoxModel<>(TestKind.class));
        testKindComponent.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(TestKind.ALL_IN_PACKAGE, ScalaBundle.message("test.run.config.test.kind.all.in.package")),
                Pair.create(TestKind.CLAZZ, ScalaBundle.message("test.run.config.test.kind.class")),
                Pair.create(TestKind.TEST_NAME, ScalaBundle.message("test.run.config.test.kind.test.name")),
                Pair.create(TestKind.REGEXP, ScalaBundle.message("test.run.config.test.kind.regular.expression"))
        ));
        testKindComponent.addItemListener(e -> onTestKindSelected((TestKind) e.getItem()));

        JComboBox<SearchForTest> searchForTestsComponent = mySearchForTest.getComponent();
        searchForTestsComponent.setModel(new EnumComboBoxModel<>(SearchForTest.class));
        searchForTestsComponent.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(SearchForTest.IN_WHOLE_PROJECT, ScalaBundle.message("test.run.config.search.scope.in.whole.project")),
                Pair.create(SearchForTest.IN_SINGLE_MODULE, ScalaBundle.message("test.run.config.search.scope.in.single.module")),
                Pair.create(SearchForTest.ACCROSS_MODULE_DEPENDENCIES, ScalaBundle.message("test.run.config.search.scope.across.module.dependencies"))
        ));
        searchForTestsComponent.addItemListener(e -> setupModuleComboBox());

        addClassChooser(ScalaBundle.message("test.run.config.choose.test.class"), myClass.getComponent(), project);
        new PackageChooserActionListener(project).setField(myPackage.getComponent());

        useUiWithSbt.setEnabled(useSbtCheckBox.isSelected());
        useSbtCheckBox.addItemListener(e -> useUiWithSbt.setEnabled(useSbtCheckBox.isSelected()));

        myJrePathEditor.setDefaultJreSelector(DefaultJreSelector.fromModuleDependencies(myModule.getComponent(), false));
        myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());
    }

    public void resetFrom(AbstractTestRunConfiguration configuration) {
        TestConfigurationData configurationData = configuration.testConfigurationData();

        setTestClassPath(configuration.getTestClassPath());
        setTestPackagePath(configuration.getTestPackagePath());
        setSearchForTest(configurationData.getSearchTest());

        initTestKindSpecificOptions(configuration);

        resetSbtOptionsFrom(configuration);

        setJavaOptions(configurationData.javaOptions());
        myCommonScalaParameters.reset(configurationData);
        myModuleSelector.reset(configuration);
        myJrePathEditor.setPathOrName(configurationData.getJrePath(), true);
        setShowProgressMessages(configurationData.getShowProgressMessages());

        suitePaths = configuration.javaSuitePaths();
    }

    private void resetSbtOptionsFrom(AbstractTestRunConfiguration configuration) {
        TestConfigurationData configurationData = configuration.testConfigurationData();
        boolean projectHasSbt = hasSbt(configuration.getProject());
        setSbtVisible(projectHasSbt); // TODO: hide if sbt support is null/None
        setSbtUiVisible(projectHasSbt && configuration.sbtSupport().allowsSbtUiRun());

        setUseSbt(configurationData.useSbt());
        setUseUiWithSbt(configurationData.useUiWithSbt());
    }

    private void createUiComponents() {
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(99, COLUMNS, JBUI.emptyInsets(), -1, -1));

        myTestKind = simpleLabeledComponent(ScalaBundle.message("test.run.config.test.kind"), new ComboBox<>());
        useSbtCheckBox = new JCheckBox();
        TextWithMnemonic.set(useSbtCheckBox, ScalaBundle.message("test.run.config.use.sbt"));
        useUiWithSbt = new JCheckBox();
        TextWithMnemonic.set(useUiWithSbt, ScalaBundle.message("test.run.config.use.ui.with.sbt"));

        myWholePanel.add(myTestKind, simpleConstraint(rowIdx, 0, 1));
        myWholePanel.add(useSbtCheckBox, simpleConstraint(rowIdx, 1, 1));
        myWholePanel.add(useUiWithSbt, simpleConstraint(rowIdx, 2, 1));
        rowIdx++;

        myClass = simpleAdd(simpleLabeledComponent(ScalaBundle.message("test.run.config.test.class"), new EditorTextFieldWithBrowseButton(myProject, true)));
        myTestName = simpleAdd(simpleLabeledComponent(ScalaBundle.message("test.run.config.test.name"), new MyMultilineEditorTextField(), true));
        myPackage = simpleAdd(simpleLabeledComponent(ScalaBundle.message("test.run.config.test.package"), new EditorTextFieldWithBrowseButton(myProject, false)));
        mySearchForTest = simpleAdd(simpleLabeledComponent(ScalaBundle.message("test.run.config.search.for.tests"), new ComboBox<>()));
        myRegex = simpleAdd(simpleLabeledComponent(ScalaBundle.message("test.run.config.regular.expressions"), new RegexpPanel(), true));

        addSeparatorBetweenTopAndBottomPanel();

        myCommonScalaParameters = simpleAdd(new CommonScalaParametersPanel());
        myModule = simpleAdd(simpleLabeledComponent(ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.label"), new ModuleDescriptionsComboBox()));
        myJrePathEditor = simpleAdd(new JrePathEditor());

        myShowProgressMessagesCheckBox = new JCheckBox();
        TextWithMnemonic.set(myShowProgressMessagesCheckBox, ScalaBundle.message("test.run.config.print.information.messages.to.console"));
        simpleAdd(myShowProgressMessagesCheckBox);

        addBottomFiller();

        JComponent anchor = myRegex.getLabel();
        mySearchForTest.setAnchor(anchor);
        myTestKind.setAnchor(anchor);
        myClass.setAnchor(anchor);
        myTestName.setAnchor(anchor);
        myPackage.setAnchor(anchor);

        myJrePathEditor.setAnchor(myModule.getLabel());
        myCommonScalaParameters.setAnchor(myModule.getLabel());
    }


    private final int COLUMNS = 3;
    private int rowIdx = 0;

    private <T extends JComponent> T simpleAdd(T component) {
        return simpleAdd(component, simpleConstraint(rowIdx));
    }

    private <T extends JComponent> T simpleAdd(T component, Object constraints) {
        myWholePanel.add(component, constraints);
        rowIdx++;
        return component;
    }

    @NotNull
    private GridConstraints simpleConstraint(int row) {
        return simpleConstraint(row, 0, COLUMNS);
    }

    @NotNull
    private GridConstraints simpleConstraint(int row, int col, int colSpan) {
        return new GridConstraints(
                row, col, 1, colSpan,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_FIXED,
                null, null, null, 0, false
        );
    }

    private <T extends JComponent> LabeledComponent<T> simpleLabeledComponent(String labelText, T component) {
        return simpleLabeledComponent(labelText, component, false);
    }

    private <T extends JComponent> LabeledComponent<T> simpleLabeledComponent(String labelText, T component, boolean isMultilineContent) {
        LabeledComponent<T> result = new LabeledComponent<>();
        result.setText(labelText);
        result.setComponent(component);
        result.setLabelLocation(BorderLayout.WEST);
        if (isMultilineContent) {
            // by default LabeledComponent's label is aligned in center, so if the inner component is multiline
            // the label has a gab in the TOP and BOTTOM. Moreover, when the inner component grows in height
            // the label "jumps" below it's previous position, which looks akward
            // This is a workaround to make label position fixed in the TOP
            int TOP_PADDING = 5;
            JBLabel label = result.getLabel();
            label.setVerticalAlignment(SwingConstants.TOP);
            label.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.insetsTop((int) TOP_PADDING)));
        }
        return result;
    }

    private void addSeparatorBetweenTopAndBottomPanel() {
        Dimension dim = new Dimension(-1, 32);
        simpleAdd(new Spacer(), new GridConstraints(rowIdx, 0, 1, COLUMNS,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                dim, dim, dim, 0, false
        ));
        simpleAdd(new JSeparator(SwingConstants.HORIZONTAL));
    }

    private void addBottomFiller() {
        simpleAdd(new Spacer(), new GridConstraints(
                rowIdx, 0, 1, COLUMNS, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL,
                GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_WANT_GROW | GridConstraints.SIZEPOLICY_CAN_GROW,
                null, null, null, 0, false
        ));
    }

    private void initTestKindSpecificOptions(AbstractTestRunConfiguration configuration) {
        TestConfigurationData configurationData = configuration.testConfigurationData();
        TestKind kind = configurationData.getKind();

        switch (kind) {
            case ALL_IN_PACKAGE:
                setPackageEnabled();
                break;
            case CLAZZ:
                setClassEnabled();
                break;
            case TEST_NAME:
                SingleTestData singleData = (SingleTestData) configurationData;
                setTestName(singleData.getTestName());
                setTestNameEnabled();
                break;
            case REGEXP:
                RegexpTestData regexpData = (RegexpTestData) configurationData;
                setRegexps(regexpData.getClassRegexps(), regexpData.getTestRegexps());
                setRegexpEnabled();
        }
    }

    private void onTestKindSelected(TestKind testKind) {
        myModule.setEnabled(true);
        switch (testKind) {
            case ALL_IN_PACKAGE:
                setPackageEnabled();
                setupModuleComboBox();
                break;
            case CLAZZ:
                setClassEnabled();
                break;
            case TEST_NAME:
                setTestNameEnabled();
                break;
            case REGEXP:
                setRegexpEnabled();
        }
    }

    private void setupModuleComboBox() {
        SearchForTest selectedItem = (SearchForTest) mySearchForTest.getComponent().getSelectedItem();
        if (selectedItem == null) return;
        switch (selectedItem) {
            case IN_WHOLE_PROJECT:
                myModule.setEnabled(false);
                break;
            case IN_SINGLE_MODULE:
                myModule.setEnabled(true);
                break;
            case ACCROSS_MODULE_DEPENDENCIES:
                myModule.setEnabled(true);
                break;
            default:
        }
    }

    private void setSbtVisible(boolean visible) {
        useSbtCheckBox.setVisible(visible);
    }

    private void setSbtUiVisible(boolean visible) {
        useUiWithSbt.setVisible(visible);
    }

    private void setPackageVisible(boolean visible) {
        myPackage.setVisible(visible);
        mySearchForTest.setVisible(visible);
    }

    private void setClassVisible(boolean visible) {
        myClass.setVisible(visible);
    }

    private void setTestNameVisible(boolean visible) {
        myTestName.setVisible(visible);
        myClass.setVisible(visible);
    }

    private void setRegexpVisible(boolean visible) {
        myRegex.setVisible(visible);
    }

    private void disableAll() {
        setPackageVisible(false);
        setClassVisible(false);
        setTestNameVisible(false);
        setRegexpVisible(false);
    }

    private void setPackageEnabled() {
        disableAll();
        setPackageVisible(true);
        myTestKind.getComponent().setSelectedItem(TestKind.ALL_IN_PACKAGE);
    }

    private void setClassEnabled() {
        disableAll();
        setClassVisible(true);
        myTestKind.getComponent().setSelectedItem(TestKind.CLAZZ);
    }

    private void setTestNameEnabled() {
        disableAll();
        setTestNameVisible(true);
        myTestKind.getComponent().setSelectedItem(TestKind.TEST_NAME);
    }

    private void setRegexpEnabled() {
        disableAll();
        setRegexpVisible(true);
        myTestKind.getComponent().setSelectedItem(TestKind.REGEXP);
    }

    protected boolean hasSbt(Project project) {
        SbtSettings sbtSettings = SbtSettings.getInstance(project);
        return sbtSettings != null && !sbtSettings.getLinkedProjectsSettings().isEmpty();
    }

    public TestKind getSelectedKind() {
        return (TestKind) myTestKind.getComponent().getSelectedItem();
    }

    public SearchForTest getSearchForTest() {
        return (SearchForTest) mySearchForTest.getComponent().getSelectedItem();
    }

    private void setSearchForTest(SearchForTest searchTest) {
        mySearchForTest.getComponent().setSelectedItem(searchTest);
    }

    public String getTestClassPath() {
        return myClass.getComponent().getText();
    }

    public String getTestArgs() {
        return myCommonScalaParameters.getProgramParameters();
    }

    public String getJavaOptions() {
        return myCommonScalaParameters.getVMParameters();
    }

    public String getTestPackagePath() {
        return myPackage.getComponent().getText();
    }

    public String getWorkingDirectory() {
        return myCommonScalaParameters.getWorkingDirectoryAccessor().getText();
    }

    public Map<String, String> getEnvironmentVariables() {
        return myCommonScalaParameters.getEnvironmentVariables();
    }

    private void setTestClassPath(String s) {
        myClass.getComponent().setText(s);
    }

    private void setJavaOptions(String s) {
        myCommonScalaParameters.setVMParameters(s);
    }

    private void setTestPackagePath(String s) {
        myPackage.getComponent().setText(s);
    }

    private void setWorkingDirectory(String s) {
        myCommonScalaParameters.setWorkingDirectory(s);
    }

    private void setUseSbt(boolean b) {
        useSbtCheckBox.setSelected(b);
    }

    private void setUseUiWithSbt(boolean b) {
        useUiWithSbt.setSelected(b);
    }

    private void setRegexps(String[] classRegexps, String[] testRegexps) {
        myRegex.getComponent().setRegexps(classRegexps, testRegexps);
    }

    protected String[] getRegexpTableColumn(int column) {
        return myRegex.getComponent().getRegexpTableColumn(column);
    }

    public String[] getClassRegexps() {
        return getRegexpTableColumn(0);
    }

    public String[] getTestRegexps() {
        return getRegexpTableColumn(1);
    }

    public boolean getUseSbt() {
        return useSbtCheckBox.isSelected();
    }

    public boolean getUseUiWithSbt() {
        return useUiWithSbt.isSelected();
    }

    public String getTestName() {
        return myTestName.getComponent().getText();
    }

    private void setTestName(String s) {
        myTestName.getComponent().setText(s);
    }

    public boolean getShowProgressMessages() {
        return myShowProgressMessagesCheckBox.isSelected();
    }

    private void setShowProgressMessages(boolean b) {
        myShowProgressMessagesCheckBox.setSelected(b);
    }

    public JPanel getPanel() {
        return myWholePanel;
    }

    public Module getModule() {
        return myModuleSelector.getModule();
    }

    public String getJrePath() {
        return myJrePathEditor.getJrePathOrName();
    }

    private void addClassChooser(final String title,
                                 final EditorTextFieldWithBrowseButton textField,
                                 final Project project) {
        ClassBrowser browser = new ClassBrowser(project, title) {
            protected ClassFilter.ClassFilterWithScope getFilter() {
                return new ClassFilter.ClassFilterWithScope() {
                    public GlobalSearchScope getScope() {
                        Module module = getModule();
                        if (module != null) return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
                        return GlobalSearchScope.allScope(project);
                    }

                    public boolean isAccepted(PsiClass aClass) {
                        if (!getScope().accept(aClass.getContainingFile().getVirtualFile())) return false;
                        for (String suitePath : suitePaths) {
                            PsiClass[] classes = ScalaPsiManager.instance(project).getCachedClasses(getScope(), suitePath);
                            for (PsiClass psiClass : classes) {
                                if (ScalaPsiUtil.isInheritorDeep(aClass, psiClass)) return true;
                            }
                        }
                        return false;
                    }
                };
            }

            protected PsiClass findClass(String className) {
                Option<PsiClass> cachedClass = ScalaPsiManager.instance(project).getCachedClass(GlobalSearchScope.allScope(project), className);
                if (cachedClass.isEmpty()) {
                    return null;
                } else {
                    return cachedClass.get();
                }
            }
        };

        browser.setField(textField);
    }

    /**
     * copied from {@link JUnitConfigurable}
     */
    private static class PackageChooserActionListener extends BrowseModuleValueActionListener {
        public PackageChooserActionListener(final Project project) {
            super(project);
        }

        @Override
        protected String showDialog() {
            final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), getProject());
            dialog.show();
            final PsiPackage aPackage = dialog.getSelectedPackage();
            return aPackage != null ? aPackage.getQualifiedName() : null;
        }
    }

    private static class MyMultilineEditorTextField extends EditorTextField {
        public MyMultilineEditorTextField() {
            this.setOneLineMode(false);
        }

        @Override
        protected void updateBorder(@NotNull EditorEx editor) {
            setupBorder(editor); // in base class border isn't set in multiline mode
        }
    }

    /**
     * based on {@link LabeledComponent.TextWithMnemonic}
     */
    private static class TextWithMnemonic {
        final String text;
        final int mnemonicIndex;

        public TextWithMnemonic(String text, int mnemonicIndex) {
            this.text = text;
            this.mnemonicIndex = mnemonicIndex;
        }

        public static TextWithMnemonic fromTextWithMnemonic(String text) {
            int idx = UIUtil.getDisplayMnemonicIndex(text);
            return idx != -1
                   ? new TextWithMnemonic(text.substring(0, idx) + text.substring(idx + 1), idx)
                   : new TextWithMnemonic(text, -1);
        }

        public static void set(AbstractButton button, String text) {
            TextWithMnemonic.fromTextWithMnemonic(text).setTo(button);
        }

        public boolean hasMnemonic() {
            return mnemonicIndex != -1;
        }

        public char getMnemonic() {
            return text.charAt(mnemonicIndex);
        }

        public void setTo(AbstractButton button) {
            button.setText(text);
            if (hasMnemonic()) {
                button.setMnemonic(getMnemonic());
                button.setDisplayedMnemonicIndex(mnemonicIndex);
            }
        }
    }
}
