package org.jetbrains.plugins.scala.codeInsight.implicits.menu

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.implicits.{ImplicitHint, MouseHandler}
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

class MakeArgumentsExplicit extends AnAction {
  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val model = editor.getInlayModel

    val inlay = model.getElementAt(MouseHandler.mousePressLocation)
    val element = ImplicitHint.elementOf(inlay)

    val inlayText = inlay.getRenderer.asInstanceOf[HintRenderer].getText

    implicit val context: Project = e.getData(CommonDataKeys.PROJECT)

    inWriteCommandAction(element.replace(code"$element$inlayText"))(editor.getProject)

    inlay.dispose()
  }
}
