package org.jetbrains.plugins.scala.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.{EditorActionManager, EditorActionHandler}
import enterHandler.ScalaEnterHandler

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.10.2008
 */

object ScalaEditorActionsManager {
  def registerScalaActionHandlers: Unit = {
    val manager = EditorActionManager.getInstance
    registerEnterActionHandler(manager)
  }

  def registerEnterActionHandler(manager: EditorActionManager): Unit = {
    val handler = new ScalaEnterHandler(manager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER));
    manager.setActionHandler(IdeActions.ACTION_EDITOR_ENTER, handler)
  }
}