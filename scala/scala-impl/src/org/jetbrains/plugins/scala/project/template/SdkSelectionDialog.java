package org.jetbrains.plugins.scala.project.template;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.table.TableView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.project.sdkdetect.ScalaSdkProvider;
import scala.Option;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SdkSelectionDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JButton buttonDownload;
    private JButton buttonBrowse;
    private TableView<SdkChoice> myTable;

    private final JComponent myParent;
    private final SdkTableModel myTableModel = new SdkTableModel();
    private ScalaSdkDescriptor mySelectedSdk;

    private ProgressIndicator sdkScanIndicator;

    public SdkSelectionDialog(JComponent parent, VirtualFile contextDirectory) {
        super((Window) parent.getTopLevelAncestor());

        myParent = parent;

        setTitle("Select JAR's for the new Scala SDK");

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonDownload.addActionListener(e -> onDownload());
        buttonBrowse.addActionListener(e -> onBrowse());
        buttonOK.addActionListener(e -> onOK());
        buttonCancel.addActionListener(e -> onCancel());

        myTable.setModelAndUpdateColumns(myTableModel);
        myTable.getSelectionModel().addListSelectionListener(new SdkSelectionListener());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


        createScanTask(contextDirectory, null).queue();

    }

    @NotNull
    private Task.Backgroundable createScanTask(VirtualFile contextDirectory, @Nullable Runnable whenFinished) {
        Project maybeProject = ProjectUtil.guessCurrentProject(myParent);
        return new Task.Backgroundable(null,
                ScalaBundle.message("sdk.scan.title", ""),
                true,
                () -> !maybeProject.isDefault()) {

            private void addToTable(SdkChoice sdkChoice) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    int previousSelection = myTable.getSelectedRow();
                    myTableModel.addRow(sdkChoice);
                    myTableModel.fireTableDataChanged();
                    if (previousSelection >= 0)
                        myTable.getSelectionModel().setSelectionInterval(previousSelection, previousSelection);
                });
            }

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                sdkScanIndicator = indicator;
                ScalaSdkProvider scalaSdkProvider = new ScalaSdkProvider(indicator, contextDirectory);
                scalaSdkProvider.discoverSDKs(this::addToTable);
                if (whenFinished != null) {
                    whenFinished.run();
                }
                sdkScanIndicator = null;
            }
        };
    }

    private int setSelectionInterval(String version) {
        for (int i = 0; i < myTable.getRowCount(); i++) {
            if ("Ivy".equals(myTable.getValueAt(i, 0)) &&
                    version.equals(myTable.getValueAt(i, 1))) {
                return i;
            }
        }

        throw new RuntimeException("No Scala " + version + " in the Ivy repository");
    }

    private void onDownload() {
        Option<String> result = new VersionDialog(contentPane).showAndGetSelected();

        if (result.isDefined()) {
            for (int i = myTableModel.getRowCount() - 1; i >= 0; i--) {
                myTableModel.removeRow(i);
            }
            createScanTask(null, () -> {
                int rowIndex = setSelectionInterval(result.get());
                myTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
                onOK();
            }).queue();
        }
    }

    private void onBrowse() {
        Option<ScalaSdkDescriptor> result = SdkSelection.chooseScalaSdkFiles(myTable);

        if (result.isDefined()) {
            mySelectedSdk = result.get();
            dispose();
        }
    }

    private void onOK() {
        if (myTable.getSelectedRowCount() > 0) {
            mySelectedSdk = myTableModel.getItems().get(myTable.getSelectedRow()).sdk();
        }
        if (sdkScanIndicator != null)
            sdkScanIndicator.cancel();
        sdkScanIndicator = null;
        dispose();
    }

    private void onCancel() {
        mySelectedSdk = null;
        if (sdkScanIndicator != null)
            sdkScanIndicator.cancel();
        sdkScanIndicator = null;
        dispose();
    }

    public Option<ScalaSdkDescriptor> open() {
        pack();
        setLocationRelativeTo(myParent.getTopLevelAncestor());
        setVisible(true);
        return Option.apply(mySelectedSdk);
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
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonBrowse = new JButton();
        buttonBrowse.setText("Browse...");
        panel1.add(buttonBrowse, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonDownload = new JButton();
        buttonDownload.setText("Download...");
        panel1.add(buttonDownload, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel3.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myTable = new TableView();
        scrollPane1.setViewportView(myTable);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

    private class SdkSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            buttonOK.setEnabled(myTable.getSelectedRow() >= 0);
        }
    }
}
