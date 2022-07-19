package org.jetbrains.plugins.scala.lang.actions.editor.smartEnter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase;

public class SmartEnterActionTest extends TestCase {
    public static Test suite() {
        return new AbstractActionTestBase("/actions/editor/smartEnter") {
            @Override
            protected EditorActionHandler getMyHandler() {
                return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT);
            }
        };
    }
}
