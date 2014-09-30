package org.jetbrains.plugins.scala.configuration.template;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.table.TableView;
import scala.Option;
import scala.util.Either;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class SdkSelectionDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JButton buttonBrowse;
  private TableView<SdkChoice> myTable;

  private final JComponent myParent;
  private final SdkTableModel myTableModel = new SdkTableModel();
  private ScalaSdkDescriptor mySelectedSdk;

  public SdkSelectionDialog(JComponent parent, List<SdkChoice> skds) {
    super((Window) parent.getTopLevelAncestor());

    myParent = parent;

    setTitle("Select JAR's for the new Scala SDK");

    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

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

    myTableModel.setItems(skds);

    myTable.setModelAndUpdateColumns(myTableModel);
  }

  private void onBrowse() {
    Option<Either<String, ScalaSdkDescriptor>> result = SdkSelection.browse(myTable);

    if (result.isDefined()) {
      Either<String, ScalaSdkDescriptor> either = result.get();
      if (either.isLeft()) {
        String message = either.left().get();
        Messages.showErrorDialog(this, message);
      } else {
        mySelectedSdk = either.right().get();
        dispose();
      }
    }
  }

  private void onOK() {
    mySelectedSdk = myTableModel.getItems().get(myTable.getSelectedRow()).sdk();
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

  private class SdkSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      buttonOK.setEnabled(myTable.getSelectedRow() >= 0);
    }
  }
}
