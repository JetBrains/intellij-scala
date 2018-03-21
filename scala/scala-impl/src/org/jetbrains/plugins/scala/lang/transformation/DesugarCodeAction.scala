package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
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

    val project = event.getProject
    val file = CommonDataKeys.PSI_FILE.getData(event.getDataContext).asInstanceOf[ScalaFile]
    val editor = CommonDataKeys.EDITOR.getData(event.getDataContext)

    if (project == null || file == null || editor == null) return

    val selection = editor.getSelectionModel

    val title = s"Desugar Scala code (${selection.hasSelection.fold("selection", "file")})"

    new SelectionDialog().show(title).filter(_.nonEmpty).foreach { transformers =>
        inWriteCommandAction(project, title) {
          val range = selection.hasSelection.option {
            val document = editor.getDocument

            val marker = document.createRangeMarker(selection.getSelectionStart, selection.getSelectionEnd)
            marker.setGreedyToLeft(true)
            marker.setGreedyToRight(true)

            marker
          }

          withProgressSynchronously(title) {
            try {
              Transformer.transform(file, range, transformers)
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
