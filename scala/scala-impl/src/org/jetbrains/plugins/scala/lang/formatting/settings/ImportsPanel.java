package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.components.JBList;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettingsUtil;
import org.jetbrains.plugins.scala.util.JListCompatibility;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Alefas
 * @since 21/05/14.
 */
public class ImportsPanel extends CodeStyleAbstractPanel {
    private JPanel contentPanel;
    private JCheckBox addImportStatementInCheckBox;
    private JCheckBox addFullQualifiedImportsCheckBox;
    private JCheckBox sortImportsCheckBox;
    private JRadioButton sortLexicographicallyRb;
    private JRadioButton sortScalastyleRb;
    private JCheckBox importTheShortestPathCheckBox;
    private JPanel myImportsWithPrefixPanel;
    private JCheckBox collectImportsWithTheCheckBox;
    private JSpinner classCountSpinner;
    private JPanel importLayoutPanel;
    private JCheckBox doNotChangePathCheckBox;
    private JPanel myAlwaysUsedImportsPanel;
    private JBList referencesWithPrefixList;
    private DefaultListModel myReferencesWithPrefixModel;
    private JBList alwaysUsedImportsList;
    private DefaultListModel alwaysUsedImportsModel;
    private JBList importLayoutTable;
    private DefaultListModel myImportLayoutModel;

    public ImportsPanel(@NotNull CodeStyleSettings settings) {
        super(settings);
        classCountSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));

        referencesWithPrefixList = new JBList();
        myReferencesWithPrefixModel = new DefaultListModel();
        referencesWithPrefixList.setModel(myReferencesWithPrefixModel);
        JPanel panel = ScalaProjectSettingsUtil.getPatternListPanel(contentPanel,
                new JListCompatibility.JListContainer(referencesWithPrefixList),
                "Add pattern to use appropriate classes only with prefix", "Use References With Prefix:");
        myImportsWithPrefixPanel.add(panel, BorderLayout.CENTER);
        referencesWithPrefixList.getEmptyText().setText("No imports with prefix");

        myImportLayoutModel = new DefaultListModel();
        importLayoutTable = new JBList(myImportLayoutModel);
        panel = ScalaProjectSettingsUtil.getUnsortedPatternListPanel(contentPanel,
                new JListCompatibility.JListContainer(importLayoutTable), "Add package name", "Import Layout Manager");
        importLayoutPanel.add(panel, BorderLayout.CENTER);

        alwaysUsedImportsList = new JBList();
        alwaysUsedImportsModel = new DefaultListModel();
        alwaysUsedImportsList.setModel(alwaysUsedImportsModel);
        panel = ScalaProjectSettingsUtil.getPatternListPanel(contentPanel,
                new JListCompatibility.JListContainer(alwaysUsedImportsList),
                "Add import to always mark it as used", "Always mark as used");
        myAlwaysUsedImportsPanel.add(panel, BorderLayout.CENTER);
        alwaysUsedImportsList.getEmptyText().setText("Honestly mark imports as unused");
        ButtonGroup sortButtons = new ButtonGroup();
        sortButtons.add(sortLexicographicallyRb);
        sortButtons.add(sortScalastyleRb);
    }

    @Override
    protected String getTabTitle() {
        return "Imports";
    }

    @Override
    protected int getRightMargin() {
        return 0;
    }

    @Nullable
    @Override
    protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
        return new ScalaEditorHighlighter(null, null, scheme);
    }

    @NotNull
    @Override
    protected FileType getFileType() {
        return ScalaFileType.INSTANCE;
    }

    @Nullable
    @Override
    protected String getPreviewText() {
        return null;
    }

    public String[] getPrefixPackages() {
        String[] prefixPackages = new String[myReferencesWithPrefixModel.size()];
        for (int i = 0; i < myReferencesWithPrefixModel.size(); i++) {
            prefixPackages[i] = (String) myReferencesWithPrefixModel.elementAt(i);
        }
        Arrays.sort(prefixPackages);
        return prefixPackages;
    }

    public String[] getAlwaysUsedImports() {
        String[] alwaysUsedImports = new String[alwaysUsedImportsModel.size()];
        for (int i = 0; i < alwaysUsedImportsModel.size(); i++) {
            alwaysUsedImports[i] = (String) alwaysUsedImportsModel.elementAt(i);
        }
        Arrays.sort(alwaysUsedImports);
        return alwaysUsedImports;
    }

    public String[] getImportLayout() {
        String[] importLayout = new String[myImportLayoutModel.size()];
        for (int i = 0; i < myImportLayoutModel.size(); i++) {
            importLayout[i] = (String) myImportLayoutModel.elementAt(i);
        }
        return importLayout;
    }

    @Override
    public void apply(CodeStyleSettings settings) throws ConfigurationException {
        if (!isModified(settings)) return;

        ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        scalaCodeStyleSettings.setAddImportMostCloseToReference(addImportStatementInCheckBox.isSelected());
        scalaCodeStyleSettings.setAddFullQualifiedImports(addFullQualifiedImportsCheckBox.isSelected());
        scalaCodeStyleSettings.setDoNotChangeLocalImportsOnOptimize(doNotChangePathCheckBox.isSelected());
        scalaCodeStyleSettings.setSortImports(sortImportsCheckBox.isSelected());
        scalaCodeStyleSettings.setSortAsScalastyle(sortScalastyleRb.isSelected());
        scalaCodeStyleSettings.setCollectImports(collectImportsWithTheCheckBox.isSelected());
        scalaCodeStyleSettings.setClassCountToUseImportOnDemand((Integer) classCountSpinner.getValue());
        scalaCodeStyleSettings.setImportShortestPathForAmbiguousReferences(importTheShortestPathCheckBox.isSelected());
        scalaCodeStyleSettings.setImportsWithPrefix(getPrefixPackages());
        scalaCodeStyleSettings.setAlwaysUsedImports(getAlwaysUsedImports());
        scalaCodeStyleSettings.setImportLayout(getImportLayout());
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
        ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        if (scalaCodeStyleSettings.getClassCountToUseImportOnDemand() !=
                (Integer) classCountSpinner.getValue()) return true;
        if (scalaCodeStyleSettings.isAddImportMostCloseToReference() !=
                addImportStatementInCheckBox.isSelected()) return true;
        if (scalaCodeStyleSettings.isAddFullQualifiedImports() !=
                addFullQualifiedImportsCheckBox.isSelected()) return true;
        if (scalaCodeStyleSettings.isDoNotChangeLocalImportsOnOptimize() !=
                doNotChangePathCheckBox.isSelected()) return true;
        if (scalaCodeStyleSettings.isSortImports() !=
                sortImportsCheckBox.isSelected()) return true;
        if (scalaCodeStyleSettings.isCollectImports() !=
                collectImportsWithTheCheckBox.isSelected()) return true;
        if (scalaCodeStyleSettings.isImportShortestPathForAmbiguousReferences() !=
                importTheShortestPathCheckBox.isSelected()) return true;
        if (scalaCodeStyleSettings.isSortAsScalastyle() !=
                sortScalastyleRb.isSelected()) return true;
        if (!Arrays.deepEquals(scalaCodeStyleSettings.getImportsWithPrefix(), getPrefixPackages())) return true;
        if (!Arrays.deepEquals(scalaCodeStyleSettings.getAlwaysUsedImports(), getAlwaysUsedImports())) return true;
        if (!Arrays.deepEquals(scalaCodeStyleSettings.getImportLayout(), getImportLayout())) return true;
        return false;
    }

    @Nullable
    @Override
    public JComponent getPanel() {
        return contentPanel;
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
        ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

        setValue(addImportStatementInCheckBox, scalaCodeStyleSettings.isAddImportMostCloseToReference());
        setValue(addFullQualifiedImportsCheckBox, scalaCodeStyleSettings.isAddFullQualifiedImports());
        setValue(doNotChangePathCheckBox, scalaCodeStyleSettings.isDoNotChangeLocalImportsOnOptimize());
        setValue(sortImportsCheckBox, scalaCodeStyleSettings.isSortImports());
        setValue(sortScalastyleRb, scalaCodeStyleSettings.isSortAsScalastyle());
        setValue(sortLexicographicallyRb, !scalaCodeStyleSettings.isSortAsScalastyle());
        setValue(collectImportsWithTheCheckBox, scalaCodeStyleSettings.isCollectImports());
        setValue(classCountSpinner, scalaCodeStyleSettings.getClassCountToUseImportOnDemand());
        setValue(importTheShortestPathCheckBox, scalaCodeStyleSettings.isImportShortestPathForAmbiguousReferences());

        myReferencesWithPrefixModel.clear();
        for (String aPackage : scalaCodeStyleSettings.getImportsWithPrefix()) {
            myReferencesWithPrefixModel.add(myReferencesWithPrefixModel.size(), aPackage);
        }

        alwaysUsedImportsModel.clear();
        for (String importPattern : scalaCodeStyleSettings.getAlwaysUsedImports()) {
            alwaysUsedImportsModel.add(alwaysUsedImportsModel.size(), importPattern);
        }

        myImportLayoutModel.clear();
        for (String layoutElement : scalaCodeStyleSettings.getImportLayout()) {
            myImportLayoutModel.add(myImportLayoutModel.size(), layoutElement);
        }
    }

    private static void setValue(JSpinner spinner, int value) {
        spinner.setValue(value);
    }

    private static void setValue(final JCheckBox box, final boolean value) {
        box.setSelected(value);
    }


    private static void setValue(final JRadioButton rb, final boolean value) {
        rb.setSelected(value);
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
        contentPanel = new JPanel();
        contentPanel.setLayout(new GridLayoutManager(10, 4, new Insets(0, 0, 0, 0), -1, -1));
        contentPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null));
        final Spacer spacer1 = new Spacer();
        contentPanel.add(spacer1, new GridConstraints(9, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        addImportStatementInCheckBox = new JCheckBox();
        addImportStatementInCheckBox.setSelected(false);
        addImportStatementInCheckBox.setText("Add import statement in closest block");
        contentPanel.add(addImportStatementInCheckBox, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        addFullQualifiedImportsCheckBox = new JCheckBox();
        addFullQualifiedImportsCheckBox.setText("Add fully qualified imports");
        contentPanel.add(addFullQualifiedImportsCheckBox, new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        sortImportsCheckBox = new JCheckBox();
        sortImportsCheckBox.setText("Sort imports (for optimize imports):");
        contentPanel.add(sortImportsCheckBox, new GridConstraints(4, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        importTheShortestPathCheckBox = new JCheckBox();
        importTheShortestPathCheckBox.setSelected(true);
        importTheShortestPathCheckBox.setText("Use the shortest path, when trying to import reference with already imported name ");
        contentPanel.add(importTheShortestPathCheckBox, new GridConstraints(7, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collectImportsWithTheCheckBox = new JCheckBox();
        collectImportsWithTheCheckBox.setText("Collect imports with the same prefix into one import");
        contentPanel.add(collectImportsWithTheCheckBox, new GridConstraints(6, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
        contentPanel.add(panel1, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Class count to use import with '_':");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        classCountSpinner = new JSpinner();
        panel1.add(classCountSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel1.add(spacer3, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel1.add(spacer4, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPanel.add(panel2, new GridConstraints(8, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Classes to use only with prefix"));
        myImportsWithPrefixPanel = new JPanel();
        myImportsWithPrefixPanel.setLayout(new BorderLayout(0, 0));
        panel3.add(myImportsWithPrefixPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panel5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel5.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Import Layout"));
        importLayoutPanel = new JPanel();
        importLayoutPanel.setLayout(new BorderLayout(0, 0));
        panel5.add(importLayoutPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panel6, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel6.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Imports always marked as used"));
        myAlwaysUsedImportsPanel = new JPanel();
        myAlwaysUsedImportsPanel.setLayout(new BorderLayout(0, 0));
        panel6.add(myAlwaysUsedImportsPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        doNotChangePathCheckBox = new JCheckBox();
        doNotChangePathCheckBox.setSelected(true);
        doNotChangePathCheckBox.setText("Do not change path during optimize imports for local imports");
        contentPanel.add(doNotChangePathCheckBox, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        sortScalastyleRb = new JRadioButton();
        sortScalastyleRb.setText("scalastyle consistent");
        contentPanel.add(sortScalastyleRb, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        sortLexicographicallyRb = new JRadioButton();
        sortLexicographicallyRb.setText("lexicographically");
        contentPanel.add(sortLexicographicallyRb, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        contentPanel.add(spacer5, new GridConstraints(5, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer6 = new Spacer();
        contentPanel.add(spacer6, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(20, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPanel;
    }
}
