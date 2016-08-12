package org.jetbrains.plugins.scala.lang.refactoring.introduceField;


import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.HelpID;
import com.intellij.ui.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.util.NamedDialog;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.07.2008
 */
public class ScalaIntroduceFieldDialog extends DialogWrapper implements NamedDialog {
  private JCheckBox myDefineVarChb;
  private JCheckBox myReplaceAllChb;
  private JComboBox myTypeComboBox;
  private ComboBox myNameComboBox;
  private JRadioButton myPublicRB;
  private JRadioButton myProtectedRB;
  private JRadioButton myPrivateRB;
  private JRadioButton myInitInDeclarationRB;
  private JRadioButton myInitInLocalScopeRB;

  private JLabel myTypeLabel;
  private JLabel myNameLabel;
  private JPanel contentPane;
  private JPanel visibilityPanel;
  private JTextField protectedTextField;
  private JTextField privateTextField;
  private JPanel myLinkContainer;
  private JCheckBox mySpecifyTypeChb;
  private JButton buttonOK;
  public String myEnteredName;

  private Project project;
  private ScType[] myTypes;
  private int occurrencesCount;
  private ScalaVariableValidator validator;
  private IntroduceFieldSettings mySettings;

  private LinkedHashMap<String, ScType> myTypeMap = null;
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

    ScExpression expression = ScalaRefactoringUtil.expressionToIntroduce(ifc.element());


    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    setUpNameComboBox(ifc.possibleNames());
    setUpTypeComboBox(expression);
    setUpHyperLink(expression);
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
    if (mySpecifyTypeChb.isSelected()) {
      return myTypeMap.get(myTypeComboBox.getSelectedItem());
    }

    return null;
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
        if (myPublicRB.isSelected()) {
          mySettings.setVisibilityLevel("");
          readSettings();
        } else if (myProtectedRB.isSelected()) {
          mySettings.setVisibilityLevel(getProtectedModifierData());
          readSettings();
        } else if (myPrivateRB.isSelected()) {
          mySettings.setVisibilityLevel(getPrivateModifierData());
          readSettings();
        }
      }
    };

    myPublicRB.addChangeListener(visibilityListener);
    myPrivateRB.addChangeListener(visibilityListener);
    myProtectedRB.addChangeListener(visibilityListener);


    myPrivateRB.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (myPrivateRB.isSelected()) {
          privateTextField.setEnabled(true);
        } else privateTextField.setEnabled(false);
      }
    });

    myProtectedRB.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (myProtectedRB.isSelected()) {
          protectedTextField.setEnabled(true);
        } else protectedTextField.setEnabled(false);
      }
    });
  }

  private String getPrivateModifierData() {
    String text = privateTextField.getText();
    return text.equals("") ? "private" : "private[" + text + "]";
  }

  private String getProtectedModifierData() {
    String text = protectedTextField.getText();
    return text.equals("") ? "protected" : "protected[" + text + "]";
  }

  private void setUpDialog() {
    myReplaceAllChb.setMnemonic(KeyEvent.VK_A);
    myReplaceAllChb.setFocusable(false);
    myDefineVarChb.setMnemonic(KeyEvent.VK_V);
    myDefineVarChb.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    myVisibilityButtons = new ButtonGroup();
    myVisibilityButtons.add(myPublicRB);
    myVisibilityButtons.add(myProtectedRB);
    myVisibilityButtons.add(myPrivateRB);
    myPublicRB.setMnemonic(KeyEvent.VK_D);
    myProtectedRB.setMnemonic(KeyEvent.VK_C);
    myPrivateRB.setMnemonic(KeyEvent.VK_P);
    myPublicRB.setFocusable(false);
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
      if (myType.toString() == null) nullText = true;
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

    if (occurrencesCount > 1) {
      myReplaceAllChb.setText(myReplaceAllChb.getText() + " (" + occurrencesCount + " occurrences)");
    }

    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    if (myPrivateRB.isSelected()) {
      privateTextField.setEnabled(true);
    }

    if (myProtectedRB.isSelected()) {
      protectedTextField.setEnabled(true);
    }
  }

  private void readSettings() {
    myDefineVarChb.setEnabled(mySettings.defineVarChbEnabled());
    myInitInDeclarationRB.setEnabled(mySettings.initInDeclarationEnabled());
    myInitInLocalScopeRB.setEnabled(mySettings.initLocallyEnabled());
    myReplaceAllChb.setEnabled(mySettings.replaceAllChbEnabled());

    myDefineVarChb.setSelected(mySettings.defineVar());
    myInitInDeclarationRB.setSelected(mySettings.initInDeclaration());
    myInitInLocalScopeRB.setSelected(!mySettings.initInDeclaration());
    myReplaceAllChb.setSelected(mySettings.replaceAll());

    if (mySettings.visibilityLevel().contains("protected")) {
      myProtectedRB.setSelected(true);
    } else if (mySettings.visibilityLevel().contains("private")) {
      myPrivateRB.setSelected(true);
    } else {
      myPublicRB.setSelected(true);
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

  private boolean needTypeannotations(ScExpression expression) {
    ScalaCodeStyleSettings settings = ScalaCodeStyleSettings.getInstance(expression.getProject());

    final TypeAnnotationUtil.Visibility visibility;
    if (myPrivateRB.isSelected()) {
      visibility = TypeAnnotationUtil.Private$.MODULE$;
    } else if (myProtectedRB.isSelected()) {
      visibility = TypeAnnotationUtil.Protected$.MODULE$;
    } else {
      visibility = TypeAnnotationUtil.Public$.MODULE$;
    }

    return TypeAnnotationUtil.addTypeAnnotation(
            TypeAnnotationUtil.requirementForProperty(false, visibility, settings), //can't declare in local scope
            settings.OVERRIDING_PROPERTY_TYPE_ANNOTATION,
            settings.SIMPLE_PROPERTY_TYPE_ANNOTATION,
            false,
            TypeAnnotationUtil.isSimple(expression)
    );
  }

  private void setUpTypeComboBox(final ScExpression expression) {
    mySpecifyTypeChb.setSelected(needTypeannotations(expression));

    myProtectedRB.addItemListener(
            new ItemListener() {
              public void itemStateChanged(ItemEvent e) {
                mySpecifyTypeChb.setSelected(needTypeannotations(expression));
              }
            }
    );

    myPublicRB.addItemListener(
            new ItemListener() {
              public void itemStateChanged(ItemEvent e) {
                mySpecifyTypeChb.setSelected(needTypeannotations(expression));
              }
            }
    );

    myPrivateRB.addItemListener(
            new ItemListener() {
              public void itemStateChanged(ItemEvent e) {
                mySpecifyTypeChb.setSelected(needTypeannotations(expression));
              }
            }
    );
  }

  private void setUpHyperLink(final ScExpression expression) {
    HyperlinkLabel link = TypeAnnotationUtil.createTypeAnnotationsHLink(project, ScalaBundle.message("default.ta.settings"));
    myLinkContainer.add(link);

    link.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        mySpecifyTypeChb.setSelected(needTypeannotations(expression));
      }
    });
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
    if (myDefineVarChb.isEnabled())
      scalaSettings.INTRODUCE_FIELD_IS_VAR = myDefineVarChb.isSelected();
    if (myReplaceAllChb.isEnabled())
      scalaSettings.INTRODUCE_FIELD_REPLACE_ALL = myReplaceAllChb.isSelected();
    if (myPublicRB.isSelected()) {
      scalaSettings.INTRODUCE_FIELD_VISIBILITY = "";
    } else if (myPrivateRB.isSelected()) {
      scalaSettings.INTRODUCE_FIELD_VISIBILITY = getPrivateModifierData();
      mySettings.setVisibilityLevel(getPrivateModifierData());
    } else if (myProtectedRB.isSelected()) {
      scalaSettings.INTRODUCE_FIELD_VISIBILITY = getProtectedModifierData();
      mySettings.setVisibilityLevel(getProtectedModifierData());
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
    contentPane.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myDefineVarChb = new JCheckBox();
    myDefineVarChb.setText("Declare variable");
    myDefineVarChb.setMnemonic('V');
    myDefineVarChb.setDisplayedMnemonicIndex(8);
    panel2.add(myDefineVarChb, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myReplaceAllChb = new JCheckBox();
    myReplaceAllChb.setText("Replace all occurrences");
    myReplaceAllChb.setMnemonic('A');
    myReplaceAllChb.setDisplayedMnemonicIndex(8);
    panel2.add(myReplaceAllChb, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myTypeLabel = new JLabel();
    myTypeLabel.setText("Field of type:");
    myTypeLabel.setDisplayedMnemonic('Y');
    myTypeLabel.setDisplayedMnemonicIndex(10);
    panel3.add(myTypeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameLabel = new JLabel();
    myNameLabel.setText("Name:");
    panel3.add(myNameLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTypeComboBox = new JComboBox();
    panel3.add(myTypeComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameComboBox = new ComboBox();
    myNameComboBox.setEditable(true);
    panel3.add(myNameComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    visibilityPanel = new JPanel();
    visibilityPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel4.add(visibilityPanel, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myPublicRB = new JRadioButton();
    myPublicRB.setText("Public");
    myPublicRB.setMnemonic('P');
    myPublicRB.setDisplayedMnemonicIndex(0);
    visibilityPanel.add(myPublicRB, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myProtectedRB = new JRadioButton();
    myProtectedRB.setText("Protected");
    myProtectedRB.setMnemonic('O');
    myProtectedRB.setDisplayedMnemonicIndex(2);
    visibilityPanel.add(myProtectedRB, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPrivateRB = new JRadioButton();
    myPrivateRB.setText("Private");
    myPrivateRB.setMnemonic('R');
    myPrivateRB.setDisplayedMnemonicIndex(1);
    visibilityPanel.add(myPrivateRB, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    protectedTextField = new JTextField();
    protectedTextField.setEnabled(false);
    visibilityPanel.add(protectedTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    privateTextField = new JTextField();
    privateTextField.setEnabled(false);
    visibilityPanel.add(privateTextField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    panel4.add(panel5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myInitInDeclarationRB = new JRadioButton();
    myInitInDeclarationRB.setText("Field declaration");
    myInitInDeclarationRB.setMnemonic('F');
    myInitInDeclarationRB.setDisplayedMnemonicIndex(0);
    panel5.add(myInitInDeclarationRB, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myInitInLocalScopeRB = new JRadioButton();
    myInitInLocalScopeRB.setText("Local scope");
    myInitInLocalScopeRB.setMnemonic('L');
    myInitInLocalScopeRB.setDisplayedMnemonicIndex(0);
    panel5.add(myInitInLocalScopeRB, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    panel5.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final TitledSeparator titledSeparator1 = new TitledSeparator();
    titledSeparator1.setText("Visibility");
    panel4.add(titledSeparator1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final TitledSeparator titledSeparator2 = new TitledSeparator();
    titledSeparator2.setText("Initialize in");
    panel4.add(titledSeparator2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JPanel panel6 = new JPanel();
    panel6.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel6, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myLinkContainer = new JPanel();
    myLinkContainer.setLayout(new BorderLayout(0, 0));
    panel6.add(myLinkContainer, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    mySpecifyTypeChb = new JCheckBox();
    this.$$$loadButtonText$$$(mySpecifyTypeChb, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("specify.return.type.explicitly"));
    panel6.add(mySpecifyTypeChb, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    contentPane.add(spacer3, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(-1, 20), null, null, 0, false));
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
    return contentPane;
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
