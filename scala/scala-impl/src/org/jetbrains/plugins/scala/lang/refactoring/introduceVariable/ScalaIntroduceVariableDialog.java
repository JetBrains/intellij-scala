package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable;


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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
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

import static org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator$.MODULE$;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.07.2008
 */
public class ScalaIntroduceVariableDialog extends DialogWrapper implements NamedDialog {
  private JCheckBox declareVariableCheckBox;
  private JCheckBox myCbReplaceAllOccurences;
  private JComboBox myTypeComboBox;
  private ComboBox myNameComboBox;
  private JLabel myTypeLabel;
  private JLabel myNameLabel;
  private JPanel contentPane;
  private JButton buttonOK;
  public String myEnteredName;

  private Project project;
  private ScType[] myTypes;
  private int occurrencesCount;
  private ValidationReporter reporter;
  private String[] possibleNames;
  private ScExpression expression;
  private JPanel myLinkContainer;
  private JCheckBox mySpecifyTypeChb;

  private LinkedHashMap<String, ScType> myTypeMap = null;
  private EventListenerList myListenerList = new EventListenerList();

  private static final String REFACTORING_NAME = ScalaBundle.message("introduce.variable.title");


  public ScalaIntroduceVariableDialog(final Project project,
                                      ScType[] myTypes,
                                      int occurrencesCount,
                                      ValidationReporter reporter,
                                      String[] possibleNames,
                                      ScExpression expression) {
    super(project, true);
    this.project = project;
    this.myTypes = myTypes;
    this.occurrencesCount = occurrencesCount;
    this.reporter = reporter;
    this.possibleNames = possibleNames;
    this.expression = expression;
    setUpNameComboBox(possibleNames);

    myLinkContainer.add(TypeAnnotationUtil.createTypeAnnotationsHLink(project, ScalaBundle.message("default.ta.settings")));

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
    if (myTypeComboBox.isEnabled()) {
      return myTypeMap.get(myTypeComboBox.getSelectedItem());
    }

    return null;
  }

  private void setUpDialog() {

    myCbReplaceAllOccurences.setMnemonic(KeyEvent.VK_A);
    myCbReplaceAllOccurences.setFocusable(false);
    declareVariableCheckBox.setMnemonic(KeyEvent.VK_V);
    declareVariableCheckBox.setFocusable(false);
    myNameLabel.setLabelFor(myNameComboBox);
    myTypeLabel.setLabelFor(myTypeComboBox);

    setUpSpecifyTypeChb();
    setUpHyperLink();

    boolean nullText = false;
    for (ScType myType : myTypes) {
      if (myType.toString() == null) nullText = true;
    }
    // Type specification
    if (myTypes.length != 0 && !nullText) {
      myTypeMap = ScalaRefactoringUtil.getCompatibleTypeNames(myTypes);
      for (String typeName : myTypeMap.keySet()) {
        myTypeComboBox.addItem(typeName);
      }
    }

    // Replace occurences
    if (occurrencesCount > 1) {
      myCbReplaceAllOccurences.setSelected(false);
      myCbReplaceAllOccurences.setEnabled(true);
      myCbReplaceAllOccurences.setText(myCbReplaceAllOccurences.getText() + " (" + occurrencesCount + " occurrences)");
    } else {
      myCbReplaceAllOccurences.setSelected(false);
      myCbReplaceAllOccurences.setEnabled(false);
    }


    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.requestFocus();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

    updateEnablingTypeList();
  }

  private void setUpHyperLink() {
    HyperlinkLabel link = TypeAnnotationUtil.createTypeAnnotationsHLink(project, ScalaBundle.message("default.ta.settings"));
    myLinkContainer.add(link);

    link.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            mySpecifyTypeChb.setSelected(needsTypeAnnotation());
            updateEnablingTypeList();
          }
        });
      }
    });
  }

  // TODO Are all non-local variables now "private"?
  // TODO Is there a scope selection?
  private boolean needsTypeAnnotation() {
    return ScalaTypeAnnotationSettings$.MODULE$.apply(expression.getProject()).isTypeAnnotationRequiredFor(
        Declaration$.MODULE$.apply(Visibility$.MODULE$.apply("private"), false, false, false, false),
            Location$.MODULE$.apply(expression), Some$.MODULE$.apply(new Definition(expression)));
  }

  private void setUpSpecifyTypeChb() {
    mySpecifyTypeChb.setSelected(needsTypeAnnotation());

    mySpecifyTypeChb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTypeComboBox.setEnabled(mySpecifyTypeChb.isSelected());
        updateEnablingTypeList();
      }
    });
  }

  private void setUpNameComboBox(String[] possibleNames) {

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

  public JComponent getPreferredFocusedComponent() {
    return myNameComboBox;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doOKAction() {
    if (!reporter.isOK(this)) {
      return;
    }
    if (mySpecifyTypeChb.isEnabled()) {
      ScalaApplicationSettings.getInstance().INTRODUCE_VARIABLE_EXPLICIT_TYPE = mySpecifyTypeChb.isSelected();
    }
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
    contentPane.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), 0, 0));
    contentPane.add(panel1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    declareVariableCheckBox = new JCheckBox();
    declareVariableCheckBox.setText("Variable");
    declareVariableCheckBox.setMnemonic('V');
    declareVariableCheckBox.setDisplayedMnemonicIndex(0);
    panel1.add(declareVariableCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_SOUTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myCbReplaceAllOccurences = new JCheckBox();
    myCbReplaceAllOccurences.setText("Replace all occurrences");
    myCbReplaceAllOccurences.setMnemonic('A');
    myCbReplaceAllOccurences.setDisplayedMnemonicIndex(8);
    panel1.add(myCbReplaceAllOccurences, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myTypeLabel = new JLabel();
    myTypeLabel.setEnabled(true);
    myTypeLabel.setText("Type");
    myTypeLabel.setDisplayedMnemonic('Y');
    myTypeLabel.setDisplayedMnemonicIndex(1);
    panel2.add(myTypeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameLabel = new JLabel();
    myNameLabel.setText("Name");
    myNameLabel.setDisplayedMnemonic('N');
    myNameLabel.setDisplayedMnemonicIndex(0);
    panel2.add(myNameLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTypeComboBox = new JComboBox();
    panel2.add(myTypeComboBox, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNameComboBox = new ComboBox();
    myNameComboBox.setEditable(true);
    panel2.add(myNameComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel3.setAlignmentX(0.0f);
    panel3.setAlignmentY(0.0f);
    contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    mySpecifyTypeChb = new JCheckBox();
    mySpecifyTypeChb.setAlignmentY(0.0f);
    mySpecifyTypeChb.setBorderPainted(false);
    mySpecifyTypeChb.setBorderPaintedFlat(false);
    mySpecifyTypeChb.setContentAreaFilled(false);
    mySpecifyTypeChb.setHideActionText(false);
    mySpecifyTypeChb.setHorizontalAlignment(0);
    mySpecifyTypeChb.setHorizontalTextPosition(11);
    mySpecifyTypeChb.setInheritsPopupMenu(false);
    mySpecifyTypeChb.setMargin(new Insets(0, 0, 0, 0));
    this.$$$loadButtonText$$$(mySpecifyTypeChb, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("specify.return.type.explicitly"));
    panel3.add(mySpecifyTypeChb);
    myLinkContainer = new JPanel();
    myLinkContainer.setLayout(new BorderLayout(0, 0));
    myLinkContainer.setAlignmentX(0.0f);
    myLinkContainer.setAlignmentY(0.0f);
    panel3.add(myLinkContainer);
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
