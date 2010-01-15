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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.ReachingDefintionsCollector;
import org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs.VariableInfo;
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

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

  private ScalaExtractMethodSettings settings = null;
  private Project myProject;
  private PsiElement[] myElements;
  private boolean myHasReturn;

  private PsiElement mySibling;

  private PsiElement myScope;
  private VariableInfo[] myInput;
  private VariableInfo[] myOutput;

  public ScalaExtractMethodDialog(Project project, PsiElement[] elements, boolean hasReturn, PsiElement sibling,
                                  PsiElement scope, VariableInfo[] input, VariableInfo[] output) {
    super(project, true);

    myElements = elements;
    myProject = project;
    myHasReturn = hasReturn;
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
    setupParametersPanel();
  }

  private void setupParametersPanel() {
    ParameterTablePanel.VariableData[] data = new ParameterTablePanel.VariableData[myInput.length];
    for (int i = 0; i < myInput.length; ++i) {
      data[i] = ScalaExtractMethodUtils.convertVariableData(myInput[i]);
    }
    inputParametersPanel.add(new ScalaParameterTablePanel(myProject, data));
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

  private String[] getParamNames() {
    return new String[0]; //todo:
  }

  @Override
  protected void doOKAction() {
    settings = new ScalaExtractMethodSettings(getMethodName(), getParamNames(), getParamTypes(), getReturnTypes(),
        getVisibility(), myScope, mySibling, myElements, myHasReturn);
    super.doOKAction();
  }

  private ScType[] getParamTypes() {
    return new ScType[0]; //todo:
  }

  private ScType[] getReturnTypes() {
    return new ScType[0]; //todo:
  }

  private String getMethodName() {
    return editorTextField.getText();
  }

  private PsiElement getSibling() {
    PsiElement result = myElements[0];
    while (result.getParent() != null && result.getParent() != mySibling) result = result.getParent();
    return result;
  }

  public ScalaExtractMethodSettings getSettings() {
    return settings;
  }

  //todo: make it more general in Java and remove it from here
  private class ScalaParameterTablePanel extends ParameterTablePanel {
    public ScalaParameterTablePanel(Project project, VariableData[] variableData) {
      super(project, variableData);
      //todo: replace choosing types
    }

    @Override
    protected void updateSignature() {
      //todo:
    }

    @Override
    protected void doEnterAction() {
      //todo:
    }

    @Override
    protected void doCancelAction() {
      //todo:
    }
  }
}