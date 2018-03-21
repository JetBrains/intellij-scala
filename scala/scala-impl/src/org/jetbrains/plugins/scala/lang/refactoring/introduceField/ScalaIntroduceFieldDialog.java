package org.jetbrains.plugins.scala.lang.refactoring.introduceField;


import com.intellij.openapi.application.ApplicationManager;
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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext;
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext$;
import org.jetbrains.plugins.scala.lang.refactoring.util.NamedDialog;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil;
import org.jetbrains.plugins.scala.lang.refactoring.util.ValidationReporter;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.settings.annotations.*;
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil;
import scala.Some$;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;
import java.util.Set;

import static org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator$.MODULE$;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.07.2008
 */
public class ScalaIntroduceFieldDialog extends DialogWrapper implements NamedDialog {
  private JCheckBox myReplaceAllChb;
  private JComboBox myTypeComboBox;
  private ComboBox myNameComboBox;
  private JRadioButton myInitInDeclarationRB;
  private JRadioButton myInitInLocalScopeRB;

  private JLabel myTypeLabel;
  private JLabel myNameLabel;
  private JPanel contentPane;
  private JTextField protectedTextField;
  private JTextField privateTextField;
  private JPanel myLinkContainer;
  private JCheckBox mySpecifyTypeChb;
  private JCheckBox myVariableCheckBox;
  private JComboBox visibilityComboBox;
  private JTextField visibilityTextField;
  private JButton buttonOK;
  public String myEnteredName;

  private Project project;
  private ScType[] myTypes;
  private int occurrencesCount;
  private ValidationReporter reporter;
  private IntroduceFieldSettings mySettings;
  private ScTemplateDefinition myClass;

  private LinkedHashMap<String, ScType> myTypeMap = null;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = ScalaBundle.message("introduce.field.title");

  public ScalaIntroduceFieldDialog(IntroduceFieldContext<ScExpression> ifc, IntroduceFieldSettings<ScExpression> settings) {
    super(ifc.project(), true);
    this.project = ifc.project();
    this.myTypes = ifc.types();
    this.occurrencesCount = ifc.occurrences().size();
    this.reporter = ifc.reporter();
    this.mySettings = settings;
    this.myClass = ifc.aClass();

    ScExpression expression = ScalaRefactoringUtil.expressionToIntroduce(ifc.element());

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    setUpDialog();
    setUpNameComboBox(ifc.possibleNames());
    setUpTypeComboBox(expression);
    setUpHyperLink(expression);
    bindToSettings();
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

  private ScType getSelectedType() {
    if (mySpecifyTypeChb.isSelected()) {
      return myTypeMap.get(myTypeComboBox.getSelectedItem());
    }

    return null;
  }

  private boolean isPublic() {
    return visibilityComboBox.getSelectedItem() != null
            && visibilityComboBox.getSelectedItem().equals("Public");
  }

  private String getVisibility() {
    if (isPublic()) return "";

    if (getVisibilityEncloser().equals("")) {
      return visibilityComboBox.getSelectedItem().toString().toLowerCase() + " ";
    }

    return visibilityComboBox.getSelectedItem().toString().toLowerCase() + "[" + getVisibilityEncloser() + "] ";
  }

  private String getVisibilityEncloser() {
    return visibilityTextField.getText();
  }

  private void bindToSettings() {
    myInitInDeclarationRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySettings.setInitInDeclaration(myInitInDeclarationRB.isSelected());
        readSettings();
      }
    });

    myInitInLocalScopeRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySettings.setInitInDeclaration(myInitInDeclarationRB.isSelected());
        readSettings();
      }
    });

    myReplaceAllChb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySettings.setReplaceAll(myReplaceAllChb.isSelected());
        readSettings();
      }
    });

    myVariableCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myVariableCheckBox.isSelected()) {
          mySettings.setDefineVar(true);
        } else {
          mySettings.setDefineVar(false);
        }
        readSettings();
      }
    });

    visibilityComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        visibilityTextField.setEnabled(!isPublic());
        mySettings.setVisibilityLevel(getVisibility());
        readSettings();
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
    myReplaceAllChb.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    final String[] visibility = {"Private", "Protected", "Public"};
    for (String v : visibility) {
      visibilityComboBox.addItem(v);
    }

    ButtonGroup initButtons = new ButtonGroup();
    initButtons.add(myInitInDeclarationRB);
    initButtons.add(myInitInLocalScopeRB);
    myInitInDeclarationRB.setFocusable(false);
    myInitInLocalScopeRB.setFocusable(false);

    readSettings();

    boolean nullText = false;
    for (ScType myType : myTypes) {
      if (myType.toString() == null) {
        nullText = true;
      }
    }

    // Type specification
    if (myTypes.length == 0 || nullText) {
      myTypeComboBox.setEnabled(false);
    } else {
      TypePresentationContext context = TypePresentationContext$.MODULE$.psiElementPresentationContext(myClass);
      myTypeMap = ScalaRefactoringUtil.getCompatibleTypeNames(myTypes, context);
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
  }

  private void readSettings() {
    myVariableCheckBox.setEnabled(mySettings.defineVarChbEnabled());

    myInitInDeclarationRB.setEnabled(mySettings.initInDeclarationEnabled());
    myInitInLocalScopeRB.setEnabled(mySettings.initLocallyEnabled());
    myReplaceAllChb.setEnabled(mySettings.replaceAllChbEnabled());

    myVariableCheckBox.setSelected(mySettings.defineVar());
    myInitInDeclarationRB.setSelected(mySettings.initInDeclaration());
    myInitInLocalScopeRB.setSelected(!mySettings.initInDeclaration());
    myReplaceAllChb.setSelected(mySettings.replaceAll());

    if (mySettings.visibilityLevel().contains("protected")) {
      visibilityComboBox.setSelectedItem("Protected");
    } else if (mySettings.visibilityLevel().contains("private")) {
      visibilityComboBox.setSelectedItem("Private");
    } else {
      visibilityComboBox.setSelectedItem("Public");
    }
  }

  private void setUpNameComboBox(Set<String> possibleNames) {

      final EditorComboBoxEditor comboEditor = new StringComboboxEditor(project, ScalaFileType.INSTANCE, myNameComboBox);

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

  private boolean needsTypeAnnotation(ScExpression expression) {
    return ScalaTypeAnnotationSettings$.MODULE$.apply(expression.getProject()).isTypeAnnotationRequiredFor(
        Declaration$.MODULE$.apply(Visibility$.MODULE$.apply(getVisibility()), false, false, false, false),
            Location$.MODULE$.apply(myClass), Some$.MODULE$.apply(new Definition(expression)));
  }

  private void setUpTypeComboBox(final ScExpression expression) {
    mySpecifyTypeChb.setSelected(needsTypeAnnotation(expression));
    updateEnablingTypeList();

    mySpecifyTypeChb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnablingTypeList();
      }
    });

    visibilityComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        mySpecifyTypeChb.setSelected(needsTypeAnnotation(expression));
        updateEnablingTypeList();
      }
    });
  }

  private void setUpHyperLink(final ScExpression expression) {
    HyperlinkLabel link = TypeAnnotationUtil.createTypeAnnotationsHLink(project, ScalaBundle.message("default.ta.settings"));
    myLinkContainer.add(link);

    link.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            mySpecifyTypeChb.setSelected(needsTypeAnnotation(expression));
            updateEnablingTypeList();
          }
        });
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
    if (myVariableCheckBox.isEnabled())
      scalaSettings.INTRODUCE_FIELD_IS_VAR = myVariableCheckBox.isSelected();
    if (myReplaceAllChb.isEnabled())
      scalaSettings.INTRODUCE_FIELD_REPLACE_ALL = myReplaceAllChb.isSelected();

    scalaSettings.INTRODUCE_FIELD_VISIBILITY = getVisibility();
    mySettings.setVisibilityLevel(getVisibility());

    if (myInitInDeclarationRB.isEnabled() && myInitInLocalScopeRB.isEnabled()) {
      scalaSettings.INTRODUCE_FIELD_INITIALIZE_IN_DECLARATION = myInitInDeclarationRB.isSelected();
    }
  }

  protected void doOKAction() {
    if (!reporter.isOK(this)) return;
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
    contentPane.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), 0, -1));
    contentPane.setAlignmentX(0.0f);
    contentPane.setAlignmentY(0.0f);
    contentPane.setOpaque(true);
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), 0, 0));
    contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myTypeLabel = new JLabel();
    myTypeLabel.setText("Type");
    myTypeLabel.setDisplayedMnemonic('Y');
    myTypeLabel.setDisplayedMnemonicIndex(1);
    panel1.add(myTypeLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameLabel = new JLabel();
    myNameLabel.setText("Name");
    myNameLabel.setDisplayedMnemonic('N');
    myNameLabel.setDisplayedMnemonicIndex(0);
    panel1.add(myNameLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTypeComboBox = new JComboBox();
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    myTypeComboBox.setModel(defaultComboBoxModel1);
    panel1.add(myTypeComboBox, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameComboBox = new ComboBox();
    myNameComboBox.setEditable(true);
    panel1.add(myNameComboBox, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("Visibility");
    label1.setDisplayedMnemonic('B');
    label1.setDisplayedMnemonicIndex(4);
    panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    visibilityComboBox = new JComboBox();
    visibilityComboBox.setEnabled(true);
    panel1.add(visibilityComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    visibilityTextField = new JTextField();
    visibilityTextField.setText("");
    panel1.add(visibilityTextField, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel2.setOpaque(false);
    contentPane.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    mySpecifyTypeChb = new JCheckBox();
    mySpecifyTypeChb.setContentAreaFilled(false);
    mySpecifyTypeChb.setHorizontalAlignment(2);
    mySpecifyTypeChb.setHorizontalTextPosition(11);
    mySpecifyTypeChb.setMargin(new Insets(0, 0, 0, 0));
    this.$$$loadButtonText$$$(mySpecifyTypeChb, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("specify.return.type.explicitly"));
    panel2.add(mySpecifyTypeChb);
    myLinkContainer = new JPanel();
    myLinkContainer.setLayout(new BorderLayout(0, 0));
    myLinkContainer.setAlignmentX(0.0f);
    myLinkContainer.setAlignmentY(0.0f);
    panel2.add(myLinkContainer);
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Initialize in");
    panel3.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel3.add(spacer1, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myInitInDeclarationRB = new JRadioButton();
    myInitInDeclarationRB.setLabel("Field declaration");
    myInitInDeclarationRB.setText("Field declaration");
    myInitInDeclarationRB.setMnemonic('F');
    myInitInDeclarationRB.setDisplayedMnemonicIndex(0);
    panel3.add(myInitInDeclarationRB, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myInitInLocalScopeRB = new JRadioButton();
    myInitInLocalScopeRB.setLabel("Local scope");
    myInitInLocalScopeRB.setText("Local scope");
    myInitInLocalScopeRB.setMnemonic('L');
    myInitInLocalScopeRB.setDisplayedMnemonicIndex(0);
    panel3.add(myInitInLocalScopeRB, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    contentPane.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 2), null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    contentPane.add(spacer3, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), 0, 0));
    contentPane.add(panel4, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, 1, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myVariableCheckBox = new JCheckBox();
    myVariableCheckBox.setLabel("Variable");
    myVariableCheckBox.setMargin(new Insets(0, 0, 0, 0));
    myVariableCheckBox.setText("Variable");
    myVariableCheckBox.setMnemonic('V');
    myVariableCheckBox.setDisplayedMnemonicIndex(0);
    myVariableCheckBox.setVerticalAlignment(3);
    panel4.add(myVariableCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_SOUTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myReplaceAllChb = new JCheckBox();
    myReplaceAllChb.setMargin(new Insets(0, 0, 0, 0));
    myReplaceAllChb.setText("Replace all occurrences");
    myReplaceAllChb.setMnemonic('A');
    myReplaceAllChb.setDisplayedMnemonicIndex(8);
    myReplaceAllChb.setVerticalAlignment(1);
    panel4.add(myReplaceAllChb, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE, 1, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTypeLabel.setLabelFor(myTypeComboBox);
    label1.setLabelFor(visibilityComboBox);
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
    setOKActionEnabled(MODULE$.isIdentifier(text));
  }

  private void fireNameDataChanged() {
    Object[] list = myListenerList.getListenerList();
    for (Object aList : list) {
      if (aList instanceof DataChangedListener) {
        ((DataChangedListener) aList).dataChanged();
      }
    }
  }

  private void updateEnablingTypeList() {
    myTypeComboBox.setEnabled(mySpecifyTypeChb.isSelected());
    myTypeLabel.setEnabled(mySpecifyTypeChb.isSelected());
  }
}
