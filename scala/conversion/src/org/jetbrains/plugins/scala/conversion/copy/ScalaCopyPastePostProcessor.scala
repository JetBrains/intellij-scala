package org.jetbrains.plugins.scala
package conversion
package copy

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.settings._

class ScalaCopyPastePostProcessor extends SingularCopyPastePostProcessor[Associations](Associations.flavor) {

  override def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                                      (implicit file: PsiFile,
                                       editor: Editor): Option[Associations] = file match {
    case scalaFile: ScalaFile if !DumbService.getInstance(scalaFile.getProject).isDumb =>
      startOffsets match {
        case Array(0) => None
        case _ =>
          val ranges = startOffsets.zip(endOffsets).map {
            case (startOffset, endOffset) => TextRange.create(startOffset, endOffset)
          }

          Option(Associations.collectAssociations(ranges.toSeq: _*)(scalaFile))
      }
    case _ => None
  }

  override def processTransferableData(bounds: RangeMarker, caretOffset: Int,
                                       ref: Ref[_ >: java.lang.Boolean], value: Associations)
                                      (implicit project: Project,
                                       editor: Editor,
                                       file: ScalaFile): Unit = {
    import CodeInsightSettings._
    ScalaApplicationSettings.getInstance.ADD_IMPORTS_ON_PASTE match {
      case _ if DumbService.getInstance(project).isDumb =>
      case NO =>
      case setting =>
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        value.restore(bounds) {
          case bindingsToRestore if setting == ASK =>
            val dialog =
              new RestoreReferencesDialog(project, bindingsToRestore.map(_.path.toOption.getOrElse("")).sorted.toArray)
            dialog.show()

            val selectedPaths = dialog.getSelectedElements
            dialog.getExitCode match {
              case DialogWrapper.OK_EXIT_CODE => bindingsToRestore.filter(it => selectedPaths.contains(it.path))
              case _ => Seq.empty
            }
          case bindings => bindings
        }
    }
  }
}
