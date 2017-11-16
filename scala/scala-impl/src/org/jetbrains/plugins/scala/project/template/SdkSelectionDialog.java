package org.jetbrains.plugins.scala.project.template;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.table.TableView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.plugins.scala.extensions.package$;
import org.jetbrains.plugins.scala.project.Platform;
import scala.Function0;
import scala.Function1;
import scala.Option;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import scala.util.Failure;
import scala.util.Try;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static java.lang.String.format;

public class SdkSelectionDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JButton buttonDownload;
    private JButton buttonBrowse;
    private TableView<SdkChoice> myTable;

    private final JComponent myParent;
    private Function0<List<SdkChoice>> myProvider;
    private final SdkTableModel myTableModel = new SdkTableModel();
    private ScalaSdkDescriptor mySelectedSdk;

    public SdkSelectionDialog(JComponent parent, Function0<List<SdkChoice>> provider) {
        super((Window) parent.getTopLevelAncestor());

        myParent = parent;
        myProvider = provider;

        setTitle("Select JAR's for the new Scala SDK");

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonDownload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onDownload();
            }
        });
        buttonBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onBrowse();
            }
        });
        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });
        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        myTable.getSelectionModel().addListSelectionListener(new SdkSelectionListener());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        updateTable();
    }

    private void updateTable() {
        List<SdkChoice> sdks = myProvider.apply();

        myTableModel.setItems(sdks);
        myTable.setModelAndUpdateColumns(myTableModel);

        if (!sdks.isEmpty()) {
            myTable.getSelectionModel().setSelectionInterval(0, 0);
        }
    }

    private int rowIndexOf(String location, String platform, String version) {
        for (int i = 0; i < myTable.getRowCount(); i++) {
            if (location.equals(myTable.getValueAt(i, 0)) &&
                    platform.equals(myTable.getValueAt(i, 1)) &&
                    version.equals(myTable.getValueAt(i, 2))) {
                return i;
            }
        }
        return -1;
    }

    private void onDownload() {
        VersionDialog dialog = new VersionDialog(contentPane);

        if (dialog.showAndGet()) {
            downloadVersionWithProgress(dialog.selectedPlatform(), dialog.selectedVersion());
        }
    }

    private void downloadVersionWithProgress(Platform platform, String version) {
        Try<BoxedUnit> result = package$.MODULE$.withProgressSynchronouslyTry(
                format("Downloading %s %s", platform.name(), version),
                downloadVersion(platform, version));

        if (result.isSuccess()) {
            updateTable();

            int rowIndex = rowIndexOf("Ivy", platform.name(), version);

            if (rowIndex >= 0) {
                myTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
                onOK();
            } else {
                throw new RuntimeException(
                        format("No %s %s in the Ivy repository", platform.name(), version));
            }
        } else {
            Messages.showErrorDialog(contentPane,
                    ((Failure) result).exception().getMessage(),
                    format("Error downloading %s %s", platform.name(), version));
        }
    }

    private Function1<Function1<String, BoxedUnit>, BoxedUnit> downloadVersion(final Platform platform, final String version) {
        return new AbstractFunction1<Function1<String, BoxedUnit>, BoxedUnit>() {
            @Override
            public BoxedUnit apply(Function1<String, BoxedUnit> listener) {
                Downloader$.MODULE$.downloadScala(platform, version, listener);
                return BoxedUnit.UNIT;
            }
        };
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
        dispose();
    }

    private void onCancel() {
        mySelectedSdk = null;
        dispose();
    }

    public ScalaSdkDescriptor open() {
        pack();
        setLocationRelativeTo(myParent.getTopLevelAncestor());
        setVisible(true);
        return mySelectedSdk;
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
