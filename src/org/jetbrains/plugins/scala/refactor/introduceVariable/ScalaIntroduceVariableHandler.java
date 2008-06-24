package org.jetbrains.plugins.scala.refactor.introduceVariable;

import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.refactoring.HelpID;

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.06.2008
 */

public class ScalaIntroduceVariableHandler extends ScalaIntroduceVariableBase {
  public void showErrorMessage(String text, Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(text);
    RefactoringMessageDialog dialog = new RefactoringMessageDialog("Introduce variable refactoring", text,
            HelpID.INTRODUCE_VARIABLE, "OptionPane.errorIcon", false, project);
    dialog.show();
  }
}
