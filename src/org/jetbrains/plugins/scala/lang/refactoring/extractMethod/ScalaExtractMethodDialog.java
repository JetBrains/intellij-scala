package org.jetbrains.plugins.scala.lang.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.lang.psi.types.ScType;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.01.2010
 */
public class ScalaExtractMethodDialog extends DialogWrapper {
  private JButton buttonOK;

  private String REFACTORING_NAME = ScalaBundle.message("extract.method.title");
  private JPanel contentPane;

  private ScalaExtractMethodSettings settings = null;
  private Project myProject;
  private PsiElement myScope;
  private PsiElement[] myElements;
  private boolean myHasReturn;

  public ScalaExtractMethodDialog(Project project, PsiElement[] elements, PsiElement owner, boolean hasReturn) {
    super(project, true);

    myElements = elements;
    myScope = owner;
    myProject = project;
    myHasReturn = hasReturn;

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

  @Override
  protected JComponent createContentPane() {
    return contentPane;
  }

  private void updateOkStatus() {
    //todo:
  }

  private void setUpDialog() {
    //todo:
  }

  private String getVisibility() {
    return ""; //todo:
  }

  private String[] getParamNames() {
    return new String[0]; //todo:
  }

  @Override
  protected void doOKAction() {
    settings = new ScalaExtractMethodSettings("testName", getParamNames(), getParamTypes(), getReturnTypes(),
        getVisibility(), myScope, getSibling(), myElements, myHasReturn);
    super.doOKAction();
  }

  private ScType[] getParamTypes() {
    return new ScType[0]; //todo:
  }

  private ScType[] getReturnTypes() {
    return new ScType[0]; //todo:
  }

  private PsiElement getSibling() {
    PsiElement result = myElements[0];
    while (result.getParent() != null && result.getParent() != myScope) result = result.getParent();
    return result;
  }

  public ScalaExtractMethodSettings getSettings() {
    return settings;
  }
}
