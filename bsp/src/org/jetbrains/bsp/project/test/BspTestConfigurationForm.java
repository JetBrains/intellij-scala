package org.jetbrains.bsp.project.test;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.bsp.BspBundle;
import scala.runtime.BoxedUnit;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.stream.Collectors.*;

@SuppressWarnings("unchecked")
public class BspTestConfigurationForm extends SettingsEditor<BspTestRunConfiguration> {
    final Project project;
    JPanel mainPanel;
    JComboBox<TestMode> testModeCombobox;

    // Test classes selection form
    JPanel testClassFormWrapper;
    JTextField testClassNameRegex;
    JBList<String> matchedClassesList;
    JButton refreshClassesButton;
    CollectionListModel<String> matchedClassesModel = new CollectionListModel<>(Collections.emptyList());
    List<TestClass> lastRequestResult = Collections.emptyList();

    public BspTestConfigurationForm(Project project) {
        this.project = project;
        $$$setupUI$$$();
        { // init combo box
            for (TestMode sm : TestMode.values())
                testModeCombobox.addItem(sm);
            testModeCombobox.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED)
                    onSelectModeChanged((TestMode) e.getItem());
            });
        }
        { // init matched classes list
            matchedClassesList.setEmptyText(BspBundle.message("bsp.test.no.matched.test.classes"));
            matchedClassesList.setModel(matchedClassesModel);
            updateMatchedClassesList(Collections.emptyMap());
            testClassNameRegex.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    onClassNameRegexChanged();
                }
            });
        }
        { // init refresh classes button
            refreshClassesButton.addActionListener(ev -> fetchAndRefreshClasses());
        }
    }

    private void onClassNameRegexChanged() {
        Map<String, List<String>> matched = Collections.emptyMap();
        try {
            matched = calculateMatchedClasses();
        } catch (PatternSyntaxException ignored) {
        }
        updateMatchedClassesList(matched);
    }

    private void fetchAndRefreshClasses() {
        new FetchScalaTestClassesTask(project, xs -> {
            this.lastRequestResult = xs.stream()
                    .map(x -> new TestClass(x.getTarget().getUri(), x.getClasses()))
                    .collect(toList());
            SwingUtilities.invokeLater(() -> updateMatchedClassesList(calculateMatchedClasses()));
            return BoxedUnit.UNIT;
        }, x -> {
            x.printStackTrace();
            return BoxedUnit.UNIT;
        }).queue();
    }


    private void onSelectModeChanged(TestMode current) {
        switch (current) {
            case ALL_IN_PROJECT -> testClassFormWrapper.setVisible(false);
            case CLASS -> {
                if (lastRequestResult.isEmpty())
                    fetchAndRefreshClasses();
                testClassFormWrapper.setVisible(true);
            }
        }
    }


    private void updateMatchedClassesList(Map<String, List<String>> matchedClasses) {
        matchedClassesModel.replaceAll(matchedClasses.values().stream()
                .flatMap(List::stream)
                .sorted()
                .collect(toList()));
    }

    @Override
    protected void resetEditorFrom(@NotNull BspTestRunConfiguration conf) {
        testModeCombobox.setSelectedItem(conf.getTestMode());
        onSelectModeChanged(conf.getTestMode());
        testClassNameRegex.setText(conf.getTestClassesRegex());
        lastRequestResult = conf.lastTestClassesResponse();
        if (lastRequestResult.isEmpty() && conf.getTestMode() == TestMode.CLASS) {
            fetchAndRefreshClasses();
        } else {
            updateMatchedClassesList(calculateMatchedClasses());
        }
    }

    private Map<String, List<String>> calculateMatchedClasses() {
        Pattern regex = Pattern.compile(testClassNameRegex.getText());
        return lastRequestResult.stream()
                .flatMap(item -> item.getClasses().stream()
                        .filter(x -> regex.matcher(x).matches())
                        .map(x -> Couple.of(item.getTarget(), x)))
                .collect(groupingBy(x -> x.first, mapping(x -> x.second, toList())));
    }

    @Override
    protected void applyEditorTo(@NotNull BspTestRunConfiguration runConfig) throws ConfigurationException {
        Map<String, List<String>> matched = Collections.emptyMap();
        if (testModeCombobox.getSelectedItem() == TestMode.CLASS) {
            if (lastRequestResult == null)
                throw new ConfigurationException("No matched test classes");
            try {
                matched = calculateMatchedClasses();
            } catch (PatternSyntaxException e) {
                throw new ConfigurationException("Illegal regex");
            }
            if (matched.isEmpty()) {
                throw new ConfigurationException("No matched test classes");
            }
        }

        // Apply UI to run config
        runConfig.setLastTestClassesResponse(lastRequestResult);
        runConfig.setTestClassesRegex(testClassNameRegex.getText());
        runConfig.setMatchedTestClasses(matched);
        runConfig.setTestMode((TestMode) testModeCombobox.getSelectedItem());
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return $$$getRootComponent$$$();
    }


    enum TestMode {
        ALL_IN_PROJECT(BspBundle.message("bsp.test.all.in.project")),
        CLASS(BspBundle.message("bsp.test.scala.class"));

        final String displayText;

        TestMode(String n) {
            this.displayText = n;
        }

        @Override
        public String toString() {
            return displayText;
        }
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
        mainPanel.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        final Spacer spacer1 = new Spacer();
        mainPanel.add(spacer1, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBspBundle", "bsp.test.kind"));
        mainPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        testModeCombobox = new JComboBox();
        mainPanel.add(testModeCombobox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        testClassFormWrapper = new JPanel();
        testClassFormWrapper.setLayout(new GridLayoutManager(3, 4, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(testClassFormWrapper, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBspBundle", "bsp.test.classes.regex"));
        testClassFormWrapper.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        testClassNameRegex = new JTextField();
        testClassFormWrapper.add(testClassNameRegex, new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBspBundle", "bsp.test.matched.classes"));
        testClassFormWrapper.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JBScrollPane jBScrollPane1 = new JBScrollPane();
        testClassFormWrapper.add(jBScrollPane1, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        matchedClassesList = new JBList();
        matchedClassesList.setAutoscrolls(false);
        jBScrollPane1.setViewportView(matchedClassesList);
        refreshClassesButton = new JButton();
        refreshClassesButton.setIcon(new ImageIcon(getClass().getResource("/actions/refresh.png")));
        this.$$$loadButtonText$$$(refreshClassesButton, this.$$$getMessageFromBundle$$$("messages/ScalaBspBundle", "bsp.test.refresh"));
        testClassFormWrapper.add(refreshClassesButton, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
