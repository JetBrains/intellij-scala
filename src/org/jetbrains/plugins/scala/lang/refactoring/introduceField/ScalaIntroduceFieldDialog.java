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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.util.*;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.event.*;
import java.util.EventListener;
import java.util.HashMap;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.07.2008
 */
public class ScalaIntroduceFieldDialog extends DialogWrapper implements NamedDialog {
  private JCheckBox myExplicitTypeChb;
  private JCheckBox myDefineVarChb;
  private JCheckBox myReplaceAllChb;
  private JComboBox myTypeComboBox;
  private ComboBox myNameComboBox;
  private JRadioButton myDefaultRB;
  private JRadioButton myProtectedRB;
  private JRadioButton myPrivateRB;
  private JRadioButton myInitInDeclarationRB;
  private JRadioButton myInitInLocalScopeRB;

  private JLabel myTypeLabel;
  private JLabel myNameLabel;
  private JLabel myVisibilityLabel;
  private JPanel contentPane;
  private JButton buttonOK;
  public String myEnteredName;

  private Project project;
  private ScType[] myTypes;
  private int occurrencesCount;
  private ScalaVariableValidator validator;
  private IntroduceFieldSettings mySettings;

  private HashMap<String, ScType> myTypeMap = null;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = ScalaBundle.message("introduce.field.title");
  private ButtonGroup myVisibilityButtons;


  public ScalaIntroduceFieldDialog(IntroduceFieldContext<ScExpression> ifc, IntroduceFieldSettings<ScExpression> settings) {
    super(ifc.project(), true);
    this.project = ifc.project();
    this.myTypes = ifc.types();
    this.occurrencesCount = ifc.occurrences().length;
    this.validator = ifc.validator();
    this.mySettings = settings;

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    setUpNameComboBox(ifc.possibleNames());
    bindToSettings(ifc);
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
    return myReplaceAllChb.isSelected();
  }

  public boolean isDeclareVariable() {
    return myDefineVarChb.isSelected();
  }

  public ScType getSelectedType() {
    if (!myExplicitTypeChb.isSelected()) {
      return null;
    } else {
      return myTypeMap.get(myTypeComboBox.getSelectedItem());
    }
  }

  private void bindToSettings(final IntroduceFieldContext ifc) {
    myInitInDeclarationRB.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        mySettings.setInitInDeclaration(myInitInDeclarationRB.isSelected());
        readSettings();
      }
    });

    myInitInLocalScopeRB.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        mySettings.setInitInDeclaration(myInitInDeclarationRB.isSelected());
        readSettings();
      }
    });

    myReplaceAllChb.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        mySettings.setReplaceAll(myReplaceAllChb.isSelected());
        readSettings();
      }
    });

    myExplicitTypeChb.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        mySettings.setExplicitType(myExplicitTypeChb.isSelected());
        readSettings();
      }
    });

    myDefineVarChb.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        mySettings.setDefineVar(myDefineVarChb.isSelected());
        readSettings();
      }
    });

    ChangeListener visibilityListener = new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (myDefaultRB.isSelected()) {
          mySettings.setVisibilityLelel(ScalaApplicationSettings.VisibilityLevel.DEFAULT);
          readSettings();
        }
        else if (myProtectedRB.isSelected()) {
          mySettings.setVisibilityLelel(ScalaApplicationSettings.VisibilityLevel.PROTECTED);
          readSettings();
        }
        else if (myPrivateRB.isSelected()) {
          mySettings.setVisibilityLelel(ScalaApplicationSettings.VisibilityLevel.PRIVATE);
          readSettings();
        }
      }
    };
    myDefaultRB.addChangeListener(visibilityListener);
    myPrivateRB.addChangeListener(visibilityListener);
    myProtectedRB.addChangeListener(visibilityListener);
  }

  private void setUpDialog() {
    myReplaceAllChb.setMnemonic(KeyEvent.VK_A);
    myReplaceAllChb.setFocusable(false);
    myDefineVarChb.setMnemonic(KeyEvent.VK_V);
    myDefineVarChb.setFocusable(false);
    myExplicitTypeChb.setMnemonic(KeyEvent.VK_T);
    myExplicitTypeChb.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    myVisibilityButtons = new ButtonGroup();
    myVisibilityButtons.add(myDefaultRB);
    myVisibilityButtons.add(myProtectedRB);
    myVisibilityButtons.add(myPrivateRB);
    myDefaultRB.setMnemonic(KeyEvent.VK_D);
    myProtectedRB.setMnemonic(KeyEvent.VK_C);
    myPrivateRB.setMnemonic(KeyEvent.VK_P);
    myDefaultRB.setFocusable(false);
    myProtectedRB.setFocusable(false);
    myPrivateRB.setFocusable(false);

    ButtonGroup initButtons = new ButtonGroup();
    initButtons.add(myInitInDeclarationRB);
    initButtons.add(myInitInLocalScopeRB);
    myInitInDeclarationRB.setMnemonic(KeyEvent.VK_F);
    myInitInLocalScopeRB.setMnemonic(KeyEvent.VK_L);
    myInitInDeclarationRB.setFocusable(false);
    myInitInLocalScopeRB.setFocusable(false);

    readSettings();

    boolean nullText = false;
    for (ScType myType : myTypes) {
      if (ScTypeUtil.presentableText(myType) == null) nullText = true;
    }
    // Type specification
    if (myTypes.length == 0 || nullText) {
      myTypeComboBox.setEnabled(false);
    } else {
      myTypeMap = ScalaRefactoringUtil.getCompatibleTypeNames(myTypes);
      for (String typeName : myTypeMap.keySet()) {
        myTypeComboBox.addItem(typeName);
      }
    }

    myExplicitTypeChb.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        myTypeComboBox.setEnabled(myExplicitTypeChb.isSelected());
      }
    });

    if (occurrencesCount > 1) {
      myReplaceAllChb.setText(myReplaceAllChb.getText() + " (" + occurrencesCount + " occurrences)");
    }

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

  }

  private void readSettings() {
    myExplicitTypeChb.setEnabled(mySettings.explicitTypeChbEnabled());
    myDefineVarChb.setEnabled(mySettings.defineVarChbEnabled());
    myInitInDeclarationRB.setEnabled(mySettings.initInDeclarationEnabled());
    myInitInLocalScopeRB.setEnabled(mySettings.initLocallyEnabled());
    myReplaceAllChb.setEnabled(mySettings.replaceAllChbEnabled());

    myExplicitTypeChb.setSelected(mySettings.explicitType());
    myDefineVarChb.setSelected(mySettings.defineVar());
    myInitInDeclarationRB.setSelected(mySettings.initInDeclaration());
    myInitInLocalScopeRB.setSelected(!mySettings.initInDeclaration());
    myReplaceAllChb.setSelected(mySettings.replaceAll());
    switch (mySettings.visibilityLevel()) {
      case DEFAULT: myDefaultRB.setSelected(true);
        break;
      case PROTECTED: myProtectedRB.setSelected(true);
        break;
      case PRIVATE: myPrivateRB.setSelected(true);
        break;
    }
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

  private void saveSettings() {
    ScalaApplicationSettings scalaSettings = ScalaApplicationSettings.getInstance();
    if (myExplicitTypeChb.isEnabled())
      scalaSettings.INTRODUCE_FIELD_EXPLICIT_TYPE = myExplicitTypeChb.isSelected();
    if (myDefineVarChb.isEnabled())
      scalaSettings.INTRODUCE_FIELD_IS_VAR = myDefineVarChb.isSelected();
    if (myReplaceAllChb.isEnabled())
      scalaSettings.INTRODUCE_FIELD_REPLACE_ALL = myReplaceAllChb.isSelected();
    if (myDefaultRB.isSelected()) {
      scalaSettings.INTRODUCE_FIELD_VISIBILITY = ScalaApplicationSettings.VisibilityLevel.DEFAULT;
    }
    else if (myPrivateRB.isSelected()){
      scalaSettings.INTRODUCE_FIELD_VISIBILITY = ScalaApplicationSettings.VisibilityLevel.PRIVATE;
    }
    else if (myProtectedRB.isSelected()) {
      scalaSettings.INTRODUCE_FIELD_VISIBILITY = ScalaApplicationSettings.VisibilityLevel.PROTECTED;
    }

    if (myInitInDeclarationRB.isEnabled() && myInitInLocalScopeRB.isEnabled()) {
      scalaSettings.INTRODUCE_FIELD_INITIALIZE_IN_DECLARATION = myInitInDeclarationRB.isSelected();
    }
  }

  protected void doOKAction() {
    if (!validator.isOK(this)) return;
    saveSettings();

    mySettings.setName(getEnteredName());
    mySettings.setType(getSelectedType());

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
