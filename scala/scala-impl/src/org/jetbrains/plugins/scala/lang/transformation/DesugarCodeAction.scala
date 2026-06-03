package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

class DesugarCodeAction extends AnAction(
  ScalaBundle.message("desugar.scala.code.action.text"),
  ScalaBundle.message("desugar.scala.code.action.description"),
  /* icon = */ null
) {
  // TODO support read-only files (create duplicate scratch buffer)
  override def actionPerformed(event: AnActionEvent): Unit = {
    ScalaActionUsagesCollector.logDesugarCode(event.getProject)

    implicit val project: Project = event.getProject
    if (project == null) return

    val file = CommonDataKeys.PSI_FILE.getData(event.getDataContext).asInstanceOf[ScalaFile]
    if (file == null) return

    val editor = CommonDataKeys.EDITOR.getData(event.getDataContext)
    if (editor == null) return

    val selection = editor.getSelectionModel

    val param = if (selection.hasSelection) ScalaBundle.message("scope.selection") else ScalaBundle.message("scope.file", file)
    val title = ScalaBundle.message("desugar.scala.code.in.scope", param)

    new SelectionDialog().show(title).filter(_.nonEmpty).foreach { transformers =>
      inWriteCommandAction {
          val range = selection.hasSelection.option {
            val document = editor.getDocument

            val marker = document.createRangeMarker(selection.getSelectionStart, selection.getSelectionEnd)
            marker.setGreedyToLeft(true)
            marker.setGreedyToRight(true)

            marker
          }

          withProgressSynchronously(title) {
            try {
              Transformer.applyTransformersAndReformat(file, range, transformers)
            } finally {
              range.foreach(_.dispose())
            }
          }
        }
    }
  }

  override def update(e: AnActionEvent): Unit =
    ScalaActionUtil.enableAndShowIfInScalaFile(e)

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
}
