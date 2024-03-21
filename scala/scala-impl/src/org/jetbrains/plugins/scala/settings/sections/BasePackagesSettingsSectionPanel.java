package org.jetbrains.plugins.scala.settings.sections;

import com.intellij.compiler.options.ModuleOptionsTableModel;
import com.intellij.compiler.options.ModuleTableCellRenderer;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.ResourceBundle;

import static org.jetbrains.plugins.scala.settings.ScalaProjectSettings.getInstance;

public class BasePackagesSettingsSectionPanel extends SettingsSectionPanel {
    private JRadioButton myInheritBasePackagesRadioButton;
    private JRadioButton myUseCustomBasePackagesRadioButton;
    private JPanel myBasePackagesPanel;
    private JPanel myBasePackagesHelpPanel;
    private JPanel rootPanel;
    private final JBTable myBasePackagesTable;

    public BasePackagesSettingsSectionPanel(Project project) {
        super(project);

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

        TableSpeedSearch.installOn(myBasePackagesTable);

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

        reset();
    }

    @Override
    JComponent getRootPanel() {
        return rootPanel;
    }

    @Override
    boolean isModified() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        return scalaProjectSettings.isInheritBasePackages() != myInheritBasePackagesRadioButton.isSelected() ||
                !scalaProjectSettings.getCustomBasePackages().equals(getCustomBasePackages());
    }

    @Override
    void apply() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        scalaProjectSettings.setInheritBasePackages(myInheritBasePackagesRadioButton.isSelected());
        scalaProjectSettings.setCustomBasePackages(getCustomBasePackages());
    }

    @Override
    void reset() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);

        myInheritBasePackagesRadioButton.setSelected(scalaProjectSettings.isInheritBasePackages());
        myUseCustomBasePackagesRadioButton.setSelected(!scalaProjectSettings.isInheritBasePackages());
        myBasePackagesTable.setEnabled(!scalaProjectSettings.isInheritBasePackages());
        setCustomBasePackages(scalaProjectSettings.getCustomBasePackages());
    }


    private Map<String, String> getCustomBasePackages() {
        return ((ModuleOptionsTableModel) myBasePackagesTable.getModel()).getModuleOptions();
    }

    private void setCustomBasePackages(Map<String, String> basePackages) {
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
        rootPanel.setLayout(new GridLayoutManager(4, 3, new Insets(9, 9, 9, 9), -1, -1));
        final Spacer spacer1 = new Spacer();
        rootPanel.add(spacer1, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myInheritBasePackagesRadioButton = new JRadioButton();
        this.$$$loadButtonText$$$(myInheritBasePackagesRadioButton, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.base.package.inherit"));
        rootPanel.add(myInheritBasePackagesRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myUseCustomBasePackagesRadioButton = new JRadioButton();
        this.$$$loadButtonText$$$(myUseCustomBasePackagesRadioButton, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.base.package.custom"));
        rootPanel.add(myUseCustomBasePackagesRadioButton, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myBasePackagesPanel = new JPanel();
        myBasePackagesPanel.setLayout(new BorderLayout(0, 0));
        rootPanel.add(myBasePackagesPanel, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myBasePackagesHelpPanel = new JPanel();
        myBasePackagesHelpPanel.setLayout(new BorderLayout(0, 0));
        rootPanel.add(myBasePackagesHelpPanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        rootPanel.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(myUseCustomBasePackagesRadioButton);
        buttonGroup.add(myInheritBasePackagesRadioButton);
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
