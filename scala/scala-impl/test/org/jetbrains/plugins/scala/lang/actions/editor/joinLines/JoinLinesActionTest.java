package org.jetbrains.plugins.scala.lang.actions.editor.joinLines;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class JoinLinesActionTest extends AbstractActionTestBase {

  public JoinLinesActionTest() {
    super("/actions/editor/joinLines");
  }

  @Override
  protected EditorActionHandler getMyHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES);
  }

  public static Test suite() {
    return new JoinLinesActionTest();
  }
}
