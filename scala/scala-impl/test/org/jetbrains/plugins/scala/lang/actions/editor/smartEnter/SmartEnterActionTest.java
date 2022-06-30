package org.jetbrains.plugins.scala.lang.actions.editor.smartEnter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import junit.framework.Test;
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public class SmartEnterActionTest extends AbstractActionTestBase {

  public SmartEnterActionTest() {
    super("/actions/editor/smartEnter");
  }

  @Override
  protected EditorActionHandler getMyHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
  }

  public static Test suite() {
    return new SmartEnterActionTest();
  }
}
