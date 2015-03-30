package org.jetbrains.plugins.scala.lang.refactoring.extractMethod;

//import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.MethodSignatureComponent;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;
import scala.Option;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */
public class ScalaExtractMethodDialog extends DialogWrapper {
  private JButton buttonOK;

  private String REFACTORING_NAME = ScalaBundle.message("extract.method.title");
  private JPanel contentPane;
  private JRadioButton publicRadioButton;
  private JRadioButton protectedRadioButton;
  private JRadioButton privateRadioButton;
  private JTextField protectedTextField;
  private JTextField privateTextField;
  private JPanel methodNamePanel;
  private EditorTextField editorTextField;
  private JPanel visibilityPanel;
  private JPanel inputParametersPanel;
  private JLabel returnTypeLabel;
  private JPanel myPreviewPanel;
  private JPanel multipleOutputPanel;
  private JRadioButton tupleRB;
  private JRadioButton innerClassRB;
  private JTextField caseClassNameFld;
  private JRadioButton caseClassRB;
  private JTextField innerClassNameFld;

  private boolean isDefaultClassName = true;
  private final MethodSignatureComponent mySignaturePreview;

  private ScalaExtractMethodSettings settings = null;
  private Project myProject;
  private PsiElement[] myElements;
  private Option<ScType> myHasReturn;

  private PsiElement mySibling;

  private VariableInfo[] myInput;
  private VariableInfo[] myOutput;
  private ScalaExtractMethodDialog.ScalaParameterTablePanel parameterTablePanel;
  private boolean myLastReturn;
  private Option<ScType> myLastMeaningful = null;

  public ScalaExtractMethodDialog(Project project, PsiElement[] elements, Option<ScType> hasReturn, boolean lastReturn,
                                  PsiElement sibling, VariableInfo[] input, VariableInfo[] output,
                                  Option<ScType> lastMeaningful) {
    super(project, true);

    myElements = elements;
    myProject = project;
    myHasReturn = hasReturn;
    myLastReturn = lastReturn;
    myInput = input;
    myOutput = output;
    myLastMeaningful = lastMeaningful;
    mySignaturePreview = new MethodSignatureComponent("", project, ScalaFileType.SCALA_FILE_TYPE);
    //mySignaturePreview.setPreferredSize(new Dimension(500, 70));
    mySignaturePreview.setMinimumSize(new Dimension(500, 70));

    setModal(true);
    getRootPane().setDefaultButton(buttonOK);
    setTitle(REFACTORING_NAME);
    init();
    mySibling = sibling;
    setUpDialog();
    updateOkStatus();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return editorTextField;
  }

  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Override
  protected JComponent createContentPane() {
    return contentPane;
  }

  private void updateOkStatus() {
    setOKActionEnabled(ScalaNamesUtil.isIdentifier(getMethodName()) &&
            (!protectedRadioButton.isSelected() || getProtectedEncloser().equals("") || ScalaNamesUtil.isIdentifier(getProtectedEncloser())) &&
            (!privateRadioButton.isSelected() || getPrivateEncloser().equals("") || ScalaNamesUtil.isIdentifier(getPrivateEncloser())) &&
            (!innerClassRB.isSelected() || ScalaNamesUtil.isIdentifier(innerClassNameFld.getText())) &&
            (!caseClassRB.isSelected() || ScalaNamesUtil.isIdentifier(caseClassNameFld.getText()))
    );
  }

  private String getProtectedEncloser() {
    return protectedTextField.getText();
  }

  private String getPrivateEncloser() {
    return privateTextField.getText();
  }

  private void setUpDialog() {
    editorTextField.addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(com.intellij.openapi.editor.event.DocumentEvent event) {

      }

      @Override
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
        if (isDefaultClassName) updateClassName(StringUtil.capitalize(editorTextField.getText()));
        updateSignature();
      }
    });

    setupVisibilityPanel();
    setupParametersPanel();
    setupMultipleOutputsPanel();
    setupPreviewPanel();

    updateSettings();
  }

  private void updateClassName(String newName) {
    if (isDefaultClassName) {
      innerClassNameFld.setText(newName);
      caseClassNameFld.setText(newName);
      isDefaultClassName = true;
    }
  }

  private void setupVisibilityPanel() {
    ButtonGroup visibilityGroup = new ButtonGroup();
    visibilityGroup.add(privateRadioButton);
    visibilityGroup.add(protectedRadioButton);
    visibilityGroup.add(publicRadioButton);
    publicRadioButton.setSelected(true); //todo: ApplicationSettings?
    privateTextField.setEnabled(false);
    protectedTextField.setEnabled(false);

    privateRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (privateRadioButton.isSelected()) {
          privateTextField.setEnabled(true);
        } else privateTextField.setEnabled(false);
        updateSignature();
      }
    });

    privateTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateSignature();
      }
    });

    protectedRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (protectedRadioButton.isSelected()) {
          protectedTextField.setEnabled(true);
        } else protectedTextField.setEnabled(false);
        updateSignature();
      }
    });

    protectedTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateSignature();
      }
    });

    visibilityPanel.setVisible(isVisibilitySectionAvailable());
  }

  private void setupParametersPanel() {
    ArrayList<VariableData> data = new ArrayList<VariableData>();
    for (VariableInfo aMyInput : myInput) {
      VariableData d = ScalaExtractMethodUtils.convertVariableData(aMyInput, myElements);
      if (d != null) data.add(d);
    }
    parameterTablePanel = new ScalaParameterTablePanel(myProject, data.toArray(new VariableData[data.size()]));
    inputParametersPanel.add(parameterTablePanel);
  }

  private boolean isVisibilitySectionAvailable() {
    return mySibling.getParent() instanceof ScTemplateBody;
  }

  private String getVisibility() {
    if (!isVisibilitySectionAvailable()) return "";
    if (publicRadioButton.isSelected()) return "";
    else if (privateRadioButton.isSelected()) {
      if (getPrivateEncloser().equals("")) return "private ";
      else return "private[" + getPrivateEncloser() + "] ";
    } else {
      if (getProtectedEncloser().equals("")) return "protected ";
      else return "protected[" + getProtectedEncloser() + "] ";
    }
  }

  protected void setupMultipleOutputsPanel() {
    updateSettings();
    if (settings.outputs().length <= 1) multipleOutputPanel.setVisible(false);

    ButtonGroup outputGroup = new ButtonGroup();
    outputGroup.add(tupleRB);
    outputGroup.add(innerClassRB);
    outputGroup.add(caseClassRB);

    tupleRB.setMnemonic('T');
    innerClassRB.setMnemonic('I');
    caseClassRB.setMnemonic('C');

    ChangeListener updater = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateSignature();
        innerClassNameFld.setEnabled(innerClassRB.isSelected());
        caseClassNameFld.setEnabled(caseClassRB.isSelected());
        if (innerClassRB.isSelected()) innerClassNameFld.requestFocus();
        if (caseClassRB.isSelected()) caseClassNameFld.requestFocus();
      }
    };
    tupleRB.addChangeListener(updater);
    innerClassRB.addChangeListener(updater);
    caseClassRB.addChangeListener(updater);

    tupleRB.setSelected(true);

    caseClassNameFld.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        isDefaultClassName = false;
        updateSignature();
      }
    });
    innerClassNameFld.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        isDefaultClassName = false;
        updateSignature();
      }
    });
  }

  protected void setupPreviewPanel() {
    myPreviewPanel.setLayout(new BorderLayout());
    myPreviewPanel.add(mySignaturePreview, BorderLayout.CENTER);

    updateSignature();
  }

  protected void updateSignature() {
    updateOkStatus();
    updateSettings();
    String text = ScalaExtractMethodUtils.previewSignatureText(settings);
    mySignaturePreview.setSignature(text);
    returnTypeLabel.setText(ScalaExtractMethodUtils.calcReturnType(settings));
  }

  @Override
  protected void doOKAction() {
    updateSettings();
    super.doOKAction();
  }

  private void updateSettings() {
    boolean createClass = innerClassRB.isSelected() || caseClassRB.isSelected();
    InnerClassSettings ics = new InnerClassSettings(createClass, getClassName(), getReturns(), caseClassRB.isSelected());
    settings = new ScalaExtractMethodSettings(getMethodName(), getParameters(), getReturns(),
        getVisibility(), mySibling, myElements, myHasReturn, myLastReturn, myLastMeaningful, ics);
  }

  private String getClassName() {
    if (innerClassRB.isSelected()) return innerClassNameFld.getText();
    else if (caseClassRB.isSelected()) return caseClassNameFld.getText();
    else return "";
  }

  public ExtractMethodParameter[] getParameters() {
    VariableData[] data = parameterTablePanel.getVariableData();
    ArrayList<ExtractMethodParameter> list = new ArrayList<ExtractMethodParameter>();
    for (VariableData d : data) {
      ScalaVariableData variableData = (ScalaVariableData) d;
      ExtractMethodParameter param = ExtractMethodParameter.from(variableData);
      list.add(param);
    }
    return list.toArray(new ExtractMethodParameter[list.size()]);
  }

  public ExtractMethodOutput[] getReturns() {
    ArrayList<ExtractMethodOutput> list = new ArrayList<ExtractMethodOutput>();
    for (VariableInfo info : myOutput) {
      ScalaVariableData data = ScalaExtractMethodUtils.convertVariableData(info, myElements);
      list.add(ExtractMethodOutput.from(data));
    }
    return list.toArray(new ExtractMethodOutput[list.size()]);
  }

  private String getMethodName() {
    return editorTextField.getText();
  }

  public ScalaExtractMethodSettings getSettings() {
    return settings;
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
    methodNamePanel = new JPanel();
    methodNamePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(methodNamePanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    methodNamePanel.setBorder(BorderFactory.createTitledBorder("Method"));
    final JLabel label1 = new JLabel();
    label1.setText("Method name:");
    methodNamePanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    editorTextField = new EditorTextField();
    editorTextField.setText("");
    methodNamePanel.add(editorTextField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    visibilityPanel = new JPanel();
    visibilityPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(visibilityPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    visibilityPanel.setBorder(BorderFactory.createTitledBorder("Visibility"));
    publicRadioButton = new JRadioButton();
    publicRadioButton.setText("Public");
    publicRadioButton.setMnemonic('P');
    publicRadioButton.setDisplayedMnemonicIndex(0);
    visibilityPanel.add(publicRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    protectedRadioButton = new JRadioButton();
    protectedRadioButton.setText("Protected");
    protectedRadioButton.setMnemonic('O');
    protectedRadioButton.setDisplayedMnemonicIndex(2);
    visibilityPanel.add(protectedRadioButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    privateRadioButton = new JRadioButton();
    privateRadioButton.setText("Private");
    privateRadioButton.setMnemonic('R');
    privateRadioButton.setDisplayedMnemonicIndex(1);
    visibilityPanel.add(privateRadioButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    protectedTextField = new JTextField();
    visibilityPanel.add(protectedTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    privateTextField = new JTextField();
    visibilityPanel.add(privateTextField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    inputParametersPanel = new JPanel();
    inputParametersPanel.setLayout(new BorderLayout(0, 0));
    panel1.add(inputParametersPanel, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    inputParametersPanel.setBorder(BorderFactory.createTitledBorder("Parameters"));
    multipleOutputPanel = new JPanel();
    multipleOutputPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(multipleOutputPanel, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    multipleOutputPanel.setBorder(BorderFactory.createTitledBorder("Multiple output"));
    caseClassRB = new JRadioButton();
    caseClassRB.setText("Inner case class");
    multipleOutputPanel.add(caseClassRB, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    caseClassNameFld = new JTextField();
    caseClassNameFld.setText("");
    multipleOutputPanel.add(caseClassNameFld, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    tupleRB = new JRadioButton();
    tupleRB.setText("Tuple");
    multipleOutputPanel.add(tupleRB, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    innerClassRB = new JRadioButton();
    innerClassRB.setText("Inner class");
    multipleOutputPanel.add(innerClassRB, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    innerClassNameFld = new JTextField();
    innerClassNameFld.setText("");
    multipleOutputPanel.add(innerClassNameFld, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final Spacer spacer1 = new Spacer();
    contentPane.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(panel2, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Return type:");
    panel2.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    panel2.add(spacer2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    returnTypeLabel = new JLabel();
    returnTypeLabel.setFont(new Font(returnTypeLabel.getFont().getName(), Font.BOLD, returnTypeLabel.getFont().getSize()));
    returnTypeLabel.setText("");
    panel2.add(returnTypeLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPreviewPanel = new JPanel();
    myPreviewPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    contentPane.add(myPreviewPanel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myPreviewPanel.setBorder(BorderFactory.createTitledBorder("Signature preview"));
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return contentPane;
  }

  private class ScalaParameterTablePanel extends ParameterTablePanel {
    public ScalaParameterTablePanel(Project project, VariableData[] variableData) {
      super(project, variableData);
    }

    protected void updateSignature() {
      ScalaExtractMethodDialog.this.updateSignature();
    }

    protected void doEnterAction() {
    }

    protected void doCancelAction() {
    }
  }
}