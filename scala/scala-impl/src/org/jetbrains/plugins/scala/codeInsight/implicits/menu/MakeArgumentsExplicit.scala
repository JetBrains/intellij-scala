package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

class MakeArgumentsExplicit(element: PsiElement) extends AnAction("Make arguments explicit") {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)

    val inlay = {
      val model = editor.getInlayModel
      model.getInlineElementAt(editor.offsetToVisualPosition(element.getTextOffset))
    }

    val inlayText = inlay.getRenderer.asInstanceOf[HintRenderer].getText

    inlay.dispose()

    implicit val context: ProjectContext = ProjectContext.fromProject(e.getData(CommonDataKeys.PROJECT))

    inWriteCommandAction(element.replace(code"$element$inlayText"))(editor.getProject)
  }
}
