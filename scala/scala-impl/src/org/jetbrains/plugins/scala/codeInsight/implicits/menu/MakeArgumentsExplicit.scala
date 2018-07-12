package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.codeInsight.implicits.{Hint, MouseHandler}
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

class MakeArgumentsExplicit extends AnAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    val element = Hint.elementOf(inlay)

    val inlayText = inlay.getRenderer.asInstanceOf[HintRenderer].getText

    implicit val context: ProjectContext = ProjectContext.fromProject(e.getData(CommonDataKeys.PROJECT))

    inWriteCommandAction(editor.getProject)(element.replace(code"$element$inlayText"))

    inlay.dispose()
  }
}
