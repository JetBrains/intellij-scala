package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.StringComboboxEditor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.util.*;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.event.*;
import java.util.EventListener;
import java.util.HashMap;

/**
 * @author Alexander Podkhalyuzin
 */
public class ScalaIntroduceParameterDialog extends DialogWrapper implements NamedDialog {
  private JPanel contentPane;
  private JComboBox typeComboBox;
  private ComboBox myNameComboBox;
  private JCheckBox makeWithDefaultValueCheckBox;
  private JCheckBox replaceAllOccurrencesCheckBox;
  private JLabel myNameLabel;
  private JLabel myTypeLabel;
  private JButton buttonOK;
  private Project project;
  private ScType myType;
  private int occurrencesCount;
  private ScalaVariableValidator validator;
  private String[] possibleNames;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = ScalaBundle.message("introduce.parameter.title");
  private HashMap<String, ScType> myTypeMap;

  protected ScalaIntroduceParameterDialog(Project project,
                                          ScType myType,
                                          int occurrencesCount,
                                          ScalaVariableValidator validator,
                                          String[] possibleNames) {
    super(project, true);
    this.project = project;
    this.myType = myType;
    this.occurrencesCount = occurrencesCount;
    this.validator = validator;
    this.possibleNames = possibleNames;
    setUpNameComboBox(possibleNames);

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    updateOkStatus();
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  private void setUpNameComboBox(String[] possibleNames) {

    final EditorComboBoxEditor comboEditor = new StringComboboxEditor(project, ScalaFileType.SCALA_FILE_TYPE, myNameComboBox);

    myNameComboBox.setEditor(comboEditor);
    myNameComboBox.setRenderer(new EditorComboBoxRenderer(comboEditor));

    myNameComboBox.setEditable(true);
    myNameComboBox.setMaximumRowCount(8);
    myListenerList.add(DataChangedListener.class, new DataChangedListener());

    myNameComboBox.addItemListener(
        new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            fireNameDataChanged();
          }
        }
    );

    ((EditorTextField) myNameComboBox.getEditor().getEditorComponent()).addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
        fireNameDataChanged();
      }
    }
    );

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myNameComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    for (String possibleName : possibleNames) {
      myNameComboBox.addItem(possibleName);
    }
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
    }
  }

  @Nullable
  public String getEnteredName() {
    if (myNameComboBox.getEditor().getItem() instanceof String &&
        ((String) myNameComboBox.getEditor().getItem()).length() > 0) {
      return (String) myNameComboBox.getEditor().getItem();
    } else {
      return null;
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(ScalaNamesUtil.isIdentifier(text));
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_PARAMETER);
  }

  private void setUpDialog() {
    replaceAllOccurrencesCheckBox.setMnemonic(KeyEvent.VK_A);
    replaceAllOccurrencesCheckBox.setFocusable(false);
    makeWithDefaultValueCheckBox.setMnemonic(KeyEvent.VK_D);
    makeWithDefaultValueCheckBox.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(typeComboBox);

    // Type specification
    if (myType == null || ScTypeUtil.presentableText(myType) == null) {
      typeComboBox.setEnabled(false);
    } else {
      myTypeMap = ScalaRefactoringUtil.getCompatibleTypeNames(myType);
      for (String typeName : myTypeMap.keySet()) {
        typeComboBox.addItem(typeName);
      }
    }

    if (ScalaApplicationSettings.getInstance().INTRODUCE_PARAMETER_CREATE_DEFAULT != null) {
      makeWithDefaultValueCheckBox.setSelected(ScalaApplicationSettings.getInstance().INTRODUCE_PARAMETER_CREATE_DEFAULT);
    }

    // Replace occurences
    if (occurrencesCount > 1) {
      replaceAllOccurrencesCheckBox.setSelected(false);
      replaceAllOccurrencesCheckBox.setEnabled(true);
      replaceAllOccurrencesCheckBox.setText(replaceAllOccurrencesCheckBox.getText() +
          " (" + occurrencesCount + " occurrences)");
    } else {
      replaceAllOccurrencesCheckBox.setSelected(false);
      replaceAllOccurrencesCheckBox.setEnabled(false);
      replaceAllOccurrencesCheckBox.setVisible(false);
    }

  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public ScType getSelectedType() {
    if (myTypeMap.size() == 0) return null;
    return myTypeMap.get(typeComboBox.getSelectedItem());
  }

  public boolean isReplaceAllOccurrences() {
    return replaceAllOccurrencesCheckBox.isSelected();
  }

  public boolean isDeclareDefault() {
    return makeWithDefaultValueCheckBox.isSelected();
  }

  protected void doOKAction() {
    if (!validator.isOK(this)) {
      return;
    }
    if (makeWithDefaultValueCheckBox.isEnabled()) {
      ScalaApplicationSettings.getInstance().INTRODUCE_PARAMETER_CREATE_DEFAULT = makeWithDefaultValueCheckBox.isSelected();
    }
    super.doOKAction();
  }
}
