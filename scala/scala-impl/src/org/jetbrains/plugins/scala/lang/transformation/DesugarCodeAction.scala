package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

/**
  * @author Pavel Fatin
  */
class DesugarCodeAction extends AnAction {
  // TODO support read-only files (create duplicate scratch buffer)
  def actionPerformed(event: AnActionEvent) {
    Stats.trigger(FeatureKey.desugarCode)

    implicit val project: Project = event.getProject
    if (project == null) return

    val file = CommonDataKeys.PSI_FILE.getData(event.getDataContext).asInstanceOf[ScalaFile]
    if (file == null) return

    val editor = CommonDataKeys.EDITOR.getData(event.getDataContext)
    if (editor == null) return

    val selection = editor.getSelectionModel

    val title = s"Desugar Scala code (${selection.hasSelection.fold("selection", "file")})"

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
              Transformer.applyTransformersAndReformat(file, file, range, transformers)
            } finally {
              range.foreach(_.dispose())
            }
          }
        }
    }
  }

  override def update(e: AnActionEvent): Unit = {
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }
}
