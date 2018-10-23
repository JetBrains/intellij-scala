package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.actions.implicitArguments.ShowImplicitArgumentsAction
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner

class ImplicitArgumentsPopup(element: PsiElement) extends AnAction("Implicit Arguments Popup") {
  override def actionPerformed(e: AnActionEvent): Unit = element match {
    case ImplicitArgumentsOwner(args) =>
      val editor = e.getData(CommonDataKeys.EDITOR)
      ShowImplicitArgumentsAction.showPopup(editor, args, isConversion = false)
    case _ =>
  }
}
