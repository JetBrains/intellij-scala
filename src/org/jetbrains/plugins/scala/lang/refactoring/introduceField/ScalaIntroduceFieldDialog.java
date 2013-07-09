package org.jetbrains.plugins.scala.lang.refactoring.introduceField;


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
import org.jetbrains.annotations.NotNull;
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
 * User: Alexander Podkhalyuzin
 * Date: 01.07.2008
 */
public class ScalaIntroduceFieldDialog extends DialogWrapper implements NamedDialog {
  private JCheckBox myCbTypeSpec;
  private JCheckBox declareVariableCheckBox;
  private JCheckBox myCbReplaceAllOccurences;
  private JComboBox myTypeComboBox;
  private ComboBox myNameComboBox;
  private ButtonGroup myAccessButtons;
  private JRadioButton myDefaultRB;
  private JRadioButton myProtectedRB;
  private JRadioButton myPrivateRB;

  private JLabel myTypeLabel;
  private JLabel myNameLabel;
  private JLabel myAccessLabel;
  private JPanel contentPane;
  private JButton buttonOK;
  public String myEnteredName;

  private Project project;
  private ScType[] myTypes;
  private int occurrencesCount;
  private ScalaVariableValidator validator;
  private String[] possibleNames;

  private HashMap<String, ScType> myTypeMap = null;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = ScalaBundle.message("introduce.field.title");


  public ScalaIntroduceFieldDialog(Project project,
                                      ScType[] myTypes,
                                      int occurrencesCount,
                                      ScalaVariableValidator validator,
                                      String[] possibleNames) {
    super(project, true);
    this.project = project;
    this.myTypes = myTypes;
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

  @Nullable
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  public JComponent getContentPane() {
    return contentPane;
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

  public boolean isReplaceAllOccurrences() {
    return myCbReplaceAllOccurences.isSelected();
  }

  public boolean isDeclareVariable() {
    return declareVariableCheckBox.isSelected();
  }

  public ScType getSelectedType() {
    if (!myCbTypeSpec.isSelected() || !myCbTypeSpec.isEnabled()) {
      return null;
    } else {
      return myTypeMap.get(myTypeComboBox.getSelectedItem());
    }
  }

  private void setUpDialog() {
    myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
    myCbReplaceAllOccurences.setFocusable(false);
    declareVariableCheckBox.setMnemonic(KeyEvent.VK_V);
    declareVariableCheckBox.setFocusable(false);
    myCbTypeSpec.setMnemonic(KeyEvent.VK_T);
    myCbTypeSpec.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    myAccessButtons = new ButtonGroup();
    myAccessButtons.add(myDefaultRB);
    myAccessButtons.add(myProtectedRB);
    myAccessButtons.add(myPrivateRB);

    myDefaultRB.setMnemonic(KeyEvent.VK_D);
    myProtectedRB.setMnemonic(KeyEvent.VK_C);
    myPrivateRB.setMnemonic(KeyEvent.VK_P);

    myDefaultRB.setFocusable(false);
    myProtectedRB.setFocusable(false);
    myPrivateRB.setFocusable(false);

    ScalaApplicationSettings scalaSettings = ScalaApplicationSettings.getInstance();
    switch (scalaSettings.INTRODUCE_FIELD_MODIFIER) {
      case DEFAULT: myDefaultRB.setSelected(true);
        break;
      case PROTECTED: myProtectedRB.setSelected(true);
        break;
      case PRIVATE: myPrivateRB.setSelected(true);
        break;
    }


    boolean nullText = false;
    for (ScType myType : myTypes) {
      if (ScTypeUtil.presentableText(myType) == null) nullText = true;
    }
    // Type specification
    if (myTypes.length == 0 || nullText) {
      myCbTypeSpec.setSelected(false);
      myCbTypeSpec.setEnabled(false);
      myTypeComboBox.setEnabled(false);
    } else {
      myCbTypeSpec.setSelected(scalaSettings.INTRODUCE_FIELD_EXPLICIT_TYPE);
      myTypeComboBox.setEnabled(scalaSettings.INTRODUCE_FIELD_EXPLICIT_TYPE);
      myTypeMap = ScalaRefactoringUtil.getCompatibleTypeNames(myTypes);
      for (String typeName : myTypeMap.keySet()) {
        myTypeComboBox.addItem(typeName);
      }
    }

    declareVariableCheckBox.setSelected(scalaSettings.INTRODUCE_FIELD_IS_VAR);

    myCbTypeSpec.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        myTypeComboBox.setEnabled(myCbTypeSpec.isSelected());
      }
    });

    // Replace occurences
    myCbReplaceAllOccurences.setSelected(scalaSettings.INTRODUCE_FIELD_REPLACE_ALL);
    if (occurrencesCount > 1) {
      myCbReplaceAllOccurences.setEnabled(true);
      myCbReplaceAllOccurences.setText(myCbReplaceAllOccurences.getText() + " (" + occurrencesCount + " occurrences)");
    } else {
      myCbReplaceAllOccurences.setEnabled(false);
    }


    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

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

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    if (!validator.isOK(this)) return;

    ScalaApplicationSettings scalaSettings = ScalaApplicationSettings.getInstance();
    if (myCbTypeSpec.isEnabled())
      scalaSettings.INTRODUCE_FIELD_EXPLICIT_TYPE = myCbTypeSpec.isSelected();
    if (declareVariableCheckBox.isEnabled())
      scalaSettings.INTRODUCE_FIELD_IS_VAR = declareVariableCheckBox.isSelected();
    if (myCbReplaceAllOccurences.isEnabled())
      scalaSettings.INTRODUCE_FIELD_REPLACE_ALL = myCbReplaceAllOccurences.isSelected();
    if (myDefaultRB.isSelected())
      scalaSettings.INTRODUCE_FIELD_MODIFIER = ScalaApplicationSettings.AccessLevel.DEFAULT;
    else if (myPrivateRB.isSelected())
      scalaSettings.INTRODUCE_FIELD_MODIFIER = ScalaApplicationSettings.AccessLevel.PRIVATE;
    else if (myProtectedRB.isSelected())
      scalaSettings.INTRODUCE_FIELD_MODIFIER = ScalaApplicationSettings.AccessLevel.PROTECTED;

    super.doOKAction();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_VARIABLE);
  }

  class DataChangedListener implements EventListener {
    void dataChanged() {
      updateOkStatus();
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
}
