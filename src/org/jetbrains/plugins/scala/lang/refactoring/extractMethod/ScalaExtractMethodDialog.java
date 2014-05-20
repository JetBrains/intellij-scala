package org.jetbrains.plugins.scala.lang.refactoring.extractMethod;

import com.intellij.openapi.editor.event.DocumentEvent;
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
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaVariableData;
import scala.Option;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
      public void beforeDocumentChange(DocumentEvent event) {
      }

      public void documentChanged(DocumentEvent event) {
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
      protected void textChanged(javax.swing.event.DocumentEvent e) {
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
      protected void textChanged(javax.swing.event.DocumentEvent e) {
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
      protected void textChanged(javax.swing.event.DocumentEvent e) {
        isDefaultClassName = false;
        updateSignature();
      }
    });
    innerClassNameFld.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(javax.swing.event.DocumentEvent e) {
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

  private class ScalaParameterTablePanel extends ParameterTablePanel {
    public ScalaParameterTablePanel(Project project, VariableData[] variableData) {
      super(project, variableData);
    }
    protected void updateSignature() {
      ScalaExtractMethodDialog.this.updateSignature();
    }
    protected void doEnterAction() {}
    protected void doCancelAction() {}
  }
}