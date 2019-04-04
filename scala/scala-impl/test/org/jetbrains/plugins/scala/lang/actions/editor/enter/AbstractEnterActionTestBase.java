package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase;

abstract public class AbstractEnterActionTestBase extends AbstractActionTestBase {
  public AbstractEnterActionTestBase(String dataPath) {
    super(dataPath);
  }

  @Override
  protected void setSettings() {
    super.setSettings();
    CommonCodeStyleSettings.IndentOptions indentOptions = getCommonSettings().getIndentOptions();
    indentOptions.INDENT_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 2;
    indentOptions.TAB_SIZE = 2;
    getCommonSettings().INDENT_CASE_FROM_SWITCH = true;
  }

  protected EditorActionHandler getMyHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
  }
}
