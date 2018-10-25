package org.jetbrains.plugins.scala.actions

import com.intellij.codeInsight.hint.actions.ShowExpressionTypeAction
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import org.jetbrains.plugins.scala.ScalaBundle

/**
  * This class is here for hotkey compatibility reasons, to avoid forcing users to learn
  * new shortcut for `Show Type Info` action.
  */
class ShowTypeInfoAction extends AnAction(ScalaBundle.message("type.info")) {
  private[this] val delegate = new ShowExpressionTypeAction

  override def update(e: AnActionEvent): Unit          = ScalaActionUtil.enableAndShowIfInScalaFile(e)
  override def actionPerformed(e: AnActionEvent): Unit = delegate.actionPerformed(e)
}
