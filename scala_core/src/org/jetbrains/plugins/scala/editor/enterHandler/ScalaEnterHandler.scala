package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.{EditorWriteActionHandler, EditorActionHandler}
import com.intellij.openapi.editor.Editor

/**
 *  User: Alexander Podkhalyuzin
 *  Date: 11.10.2008
 */

class ScalaEnterHandler(private val myOriginalHandler: EditorActionHandler) extends EditorWriteActionHandler {
  private val javaHandler: ScalaEnterHandlerImpl = new ScalaEnterHandlerImpl(myOriginalHandler)
  def executeWriteAction(editor: Editor, dataContext: DataContext): Unit = {
   javaHandler.executeWriteAction(editor, dataContext)
  }

  override def isEnabled(editor: Editor, dataContext: DataContext): Boolean = {
    javaHandler.isEnabled(editor, dataContext)
  }
}