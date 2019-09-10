package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase;
import org.junit.Assert;

abstract public class AbstractEnterActionTestBase extends AbstractActionTestBase {
  public AbstractEnterActionTestBase(String dataPath) {
    super(dataPath);
  }

  @Override
  protected void setSettings(@NotNull Project project) {
    super.setSettings(project);

    getCommonSettings(project).INDENT_CASE_FROM_SWITCH = true;
  }

  protected EditorActionHandler getMyHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
  }
}
