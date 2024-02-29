package org.jetbrains.plugins.scala.settings.sections;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;
import org.jetbrains.plugins.scala.settings.SimpleMappingListCellRenderer;
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

import static org.jetbrains.plugins.scala.settings.ScalaProjectSettings.getInstance;

public class WorksheetSettingsSectionPanel extends SettingsSectionPanel {
    private static final int WORKSHEET_RUN_DELAY_MS_MINIMUM = 500;
    private static final int WORKSHEET_RUN_DELAY_MS_MAXIMUM = 5000;
    private static final int WORKSHEET_RUN_DELAY_SPINNER_STEP_SIZE = 10;

    private JComboBox<ScalaProjectSettings.ScFileMode> scTypeSelectionCombobox;
    private JSpinner outputSpinner;
    private JSpinner worksheetAutoRunDelaySpinner;
    private JCheckBox runWorksheetInTheCheckBox;
    private JCheckBox useEclipseCompatibilityModeCheckBox;
    private JCheckBox treatScalaScratchFilesCheckBox;
    private JCheckBox collapseWorksheetFoldByCheckBox;
    private JPanel rootPanel;

    protected WorksheetSettingsSectionPanel(Project project) {
        super(project);

        outputSpinner.setModel(spinnerModel(1, null, 1));

        worksheetAutoRunDelaySpinner.setModel(spinnerModel(
            WORKSHEET_RUN_DELAY_MS_MINIMUM,
            WORKSHEET_RUN_DELAY_MS_MAXIMUM,
            WORKSHEET_RUN_DELAY_SPINNER_STEP_SIZE
        ));

        scTypeSelectionCombobox.setModel(new EnumComboBoxModel<>(ScalaProjectSettings.ScFileMode.class));
        scTypeSelectionCombobox.setRenderer(SimpleMappingListCellRenderer.create(
            Pair.create(ScalaProjectSettings.ScFileMode.Worksheet, ScalaBundle.message("script.file.mode.always.worksheet")),
            Pair.create(ScalaProjectSettings.ScFileMode.Ammonite, ScalaBundle.message("script.file.mode.always.ammonite")),
            Pair.create(ScalaProjectSettings.ScFileMode.Auto, ScalaBundle.message("script.file.mode.ammonite.in.test.sources.otherwise.worksheet"))
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

        return scalaProjectSettings.getOutputLimit() != getValue(outputSpinner) ||
                scalaProjectSettings.isInProcessMode() != runWorksheetInTheCheckBox.isSelected() ||
                scalaProjectSettings.isWorksheetFoldCollapsedByDefault() != collapseWorksheetFoldByCheckBox.isSelected() ||
                scalaProjectSettings.isUseEclipseCompatibility() != useEclipseCompatibilityModeCheckBox.isSelected() ||
                scalaProjectSettings.isTreatScratchFilesAsWorksheet() != treatScalaScratchFilesCheckBox.isSelected() ||
                scalaProjectSettings.getScFileMode() != scTypeSelectionCombobox.getSelectedItem() ||
                scalaProjectSettings.getAutoRunDelay() != getValue(worksheetAutoRunDelaySpinner)
                ;
    }

    @Override
    void apply() {
        if (!isModified()) return;

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        scalaProjectSettings.setOutputLimit((Integer) outputSpinner.getValue());
        scalaProjectSettings.setInProcessMode(runWorksheetInTheCheckBox.isSelected());
        scalaProjectSettings.setWorksheetFoldCollapsedByDefault(collapseWorksheetFoldByCheckBox.isSelected());
        scalaProjectSettings.setUseEclipseCompatibility(useEclipseCompatibilityModeCheckBox.isSelected());
        scalaProjectSettings.setTreatScratchFilesAsWorksheet(treatScalaScratchFilesCheckBox.isSelected());

        Object type = scTypeSelectionCombobox.getSelectedItem();
        if (type != null) {
            ScalaProjectSettings.ScFileMode newMode = ScalaProjectSettings.ScFileMode.valueOf(type.toString());
            if (newMode != scalaProjectSettings.getScFileMode()) {
                ScalaActionUsagesCollector.logScFileModeSet(newMode, myProject);
            }
            scalaProjectSettings.setScFileMode(newMode);
        }

        scalaProjectSettings.setAutoRunDelay(getValue(worksheetAutoRunDelaySpinner));
    }

    @Override
    void reset() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        outputSpinner.setValue(scalaProjectSettings.getOutputLimit());
        runWorksheetInTheCheckBox.setSelected(scalaProjectSettings.isInProcessMode());
        collapseWorksheetFoldByCheckBox.setSelected(scalaProjectSettings.isWorksheetFoldCollapsedByDefault());
        useEclipseCompatibilityModeCheckBox.setSelected(scalaProjectSettings.isUseEclipseCompatibility());
        treatScalaScratchFilesCheckBox.setSelected(scalaProjectSettings.isTreatScratchFilesAsWorksheet());

        scTypeSelectionCombobox.setSelectedItem(scalaProjectSettings.getScFileMode());
        setValue(worksheetAutoRunDelaySpinner, scalaProjectSettings.getAutoRunDelay());
    }

    private static SpinnerNumberModel spinnerModel(Integer min, Integer max, Integer stepSize) {
        // assuming will be changed in setSettings method
        //noinspection UnnecessaryLocalVariable
        Number value = min;
        return new SpinnerNumberModel(value, min, max, stepSize);
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
        rootPanel.setLayout(new GridLayoutManager(8, 4, new Insets(9, 9, 9, 9), -1, -1));
        final Spacer spacer1 = new Spacer();
        rootPanel.add(spacer1, new GridConstraints(7, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.output.cutoff.limit"));
        rootPanel.add(label1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.treat.sc.files.as"));
        rootPanel.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scTypeSelectionCombobox = new JComboBox();
        rootPanel.add(scTypeSelectionCombobox, new GridConstraints(0, 1, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.delay.before.auto.run"));
        rootPanel.add(label3, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        outputSpinner = new JSpinner();
        rootPanel.add(outputSpinner, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.output.cutoff.limit.units"));
        rootPanel.add(label4, new GridConstraints(5, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        rootPanel.add(spacer2, new GridConstraints(5, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        rootPanel.add(spacer3, new GridConstraints(6, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        this.$$$loadLabelText$$$(label5, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.delay.before.auto.run.units"));
        rootPanel.add(label5, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        worksheetAutoRunDelaySpinner = new JSpinner();
        rootPanel.add(worksheetAutoRunDelaySpinner, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        runWorksheetInTheCheckBox = new JCheckBox();
        runWorksheetInTheCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(runWorksheetInTheCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.run.worksheet.in.the.compiler.process"));
        rootPanel.add(runWorksheetInTheCheckBox, new GridConstraints(1, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useEclipseCompatibilityModeCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(useEclipseCompatibilityModeCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.use.eclipse.compatibility.mode"));
        rootPanel.add(useEclipseCompatibilityModeCheckBox, new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        treatScalaScratchFilesCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(treatScalaScratchFilesCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.treat.scala.scratch.files.as.worksheet.files"));
        rootPanel.add(treatScalaScratchFilesCheckBox, new GridConstraints(3, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collapseWorksheetFoldByCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(collapseWorksheetFoldByCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.collapse.long.output.by.default"));
        rootPanel.add(collapseWorksheetFoldByCheckBox, new GridConstraints(4, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
