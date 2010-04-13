package org.jetbrains.plugins.scala.lang.refactoring.extractMethod;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.ui.EditorTextField;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefintionsCollector;
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo;
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext;
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ExtractMethodParameter;
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ExtractMethodReturn;
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodSettings;
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaExtractMethodUtils;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;
import scala.Option;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
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

  private ScalaExtractMethodSettings settings = null;
  private Project myProject;
  private PsiElement[] myElements;
  private Option<ScType> myHasReturn;

  private PsiElement mySibling;

  private PsiElement myScope;
  private VariableInfo[] myInput;
  private VariableInfo[] myOutput;
  private ScalaExtractMethodDialog.ScalaParameterTablePanel parameterTablePanel;
  private boolean myLastReturn;

  public ScalaExtractMethodDialog(Project project, PsiElement[] elements, Option<ScType> hasReturn, boolean lastReturn,
                                  PsiElement sibling,
                                  PsiElement scope, VariableInfo[] input, VariableInfo[] output) {
    super(project, true);

    myElements = elements;
    myProject = project;
    myHasReturn = hasReturn;
    myLastReturn = lastReturn;
    myScope = scope;
    myInput = input;
    myOutput = output;

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
        (getProtectedEncloser().equals("") || ScalaNamesUtil.isIdentifier(getProtectedEncloser())) &&
        (getPrivateEncloser().equals("") || ScalaNamesUtil.isIdentifier(getPrivateEncloser()))
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
        updateOkStatus();
      }
    });

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
      }
    });

    protectedRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (protectedRadioButton.isSelected()) {
          protectedTextField.setEnabled(true);
        } else protectedTextField.setEnabled(false);
      }
    });
    visibilityPanel.setVisible(isVisibilitySectionAvailable());

    returnTypeLabel.setText(ScalaExtractMethodUtils.calcReturnType(myHasReturn, getReturns(), myLastReturn));

    setupParametersPanel();
  }

  private void setupParametersPanel() {
    ArrayList<ParameterTablePanel.VariableData> data = new ArrayList<ParameterTablePanel.VariableData>();
    for (int i = 0; i < myInput.length; ++i) {
      ParameterTablePanel.VariableData d = ScalaExtractMethodUtils.convertVariableData(myInput[i], myElements);
      if (d != null) data.add(d);
    }
    parameterTablePanel = new ScalaParameterTablePanel(myProject, data.toArray(
        new ParameterTablePanel.VariableData[data.size()]));
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

  @Override
  protected void doOKAction() {
    settings = new ScalaExtractMethodSettings(getMethodName(), getParameters(), getReturns(),
        getVisibility(), myScope, mySibling, myElements, myHasReturn, myLastReturn);
    super.doOKAction();
  }

  public ExtractMethodParameter[] getParameters() {
    ParameterTablePanel.VariableData[] data = parameterTablePanel.getVariableData();
    ArrayList<ExtractMethodParameter> list = new ArrayList<ExtractMethodParameter>();
    for (ParameterTablePanel.VariableData d : data) {
      ScalaExtractMethodUtils.ScalaVariableData variableData = (ScalaExtractMethodUtils.ScalaVariableData) d;
      ExtractMethodParameter param = ScalaExtractMethodUtils.getParameter(d, variableData);
      list.add(param);
    }
    return list.toArray(new ExtractMethodParameter[list.size()]);
  }

  public ExtractMethodReturn[] getReturns() {
    ArrayList<ExtractMethodReturn> list = new ArrayList<ExtractMethodReturn>();
    for (VariableInfo info : myOutput) {
      ScalaExtractMethodUtils.ScalaVariableData data =
          (ScalaExtractMethodUtils.ScalaVariableData) ScalaExtractMethodUtils.convertVariableData(info, myElements);
      ScalaExtractMethodUtils.FakePsiType tp = (ScalaExtractMethodUtils.FakePsiType) data.type;
      ExtractMethodReturn aReturn = new ExtractMethodReturn(info.element().getName(), tp.tp(), 
          data.isInsideOfElements(), ScalaPsiUtil.nameContext(info.element()) instanceof ScValue ||
          ScalaPsiUtil.nameContext(info.element()) instanceof ScFunction);
      list.add(aReturn);
    }
    return list.toArray(new ExtractMethodReturn[list.size()]);
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
    protected void updateSignature() {}
    protected void doEnterAction() {}
    protected void doCancelAction() {}
  }
}