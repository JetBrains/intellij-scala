package org.jetbrains.plugins.scala.lang.actions.editor.joinLines;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.plugins.scala.lang.actions.AbstractActionTestBase;

public class JoinLinesActionTest extends TestCase {
    public static Test suite() {
        return new AbstractActionTestBase("/actions/editor/joinLines") {
            @Override
            protected EditorActionHandler getMyHandler() {
                return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES);
            }
        };
    }
}
