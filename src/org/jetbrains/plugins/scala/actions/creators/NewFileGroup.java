package org.jetbrains.plugins.scala.actions.creators;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.Nullable;

/**
 * @author Ilya.Sergey

 */
public class NewFileGroup extends ActionGroup {

  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return new AnAction[0];
  }
}
