package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction

class RemoveExplicitArguments(element: PsiElement) extends AnAction("Remove explicit arguments") {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)

    val inlay = {
      val model = editor.getInlayModel
      model.getInlineElementAt(editor.offsetToVisualPosition(element.getTextOffset))
    }

    inlay.dispose()

    inWriteCommandAction(element.getParent.replace(element.getPrevSibling))(editor.getProject)
  }
}
