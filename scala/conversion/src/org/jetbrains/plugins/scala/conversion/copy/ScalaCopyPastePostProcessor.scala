package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.settings._

private[copy]
class ScalaCopyPastePostProcessor extends SingularCopyPastePostProcessor[Associations](Associations.flavor) {

  override def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                                      (implicit file: PsiFile,
                                       editor: Editor): Option[Associations] = {
    if (!RichCopySettings.getInstance().isEnabled)
      return None //// copy as plain text
    if (DumbService.getInstance(file.getProject).isDumb)
      return None

    if (startOffsets.isEmpty)
      return None
    val scalaFile = file match {
      case sf: ScalaFile  => sf
      case _ =>
        return None
    }

    val ranges = startOffsets.zip(endOffsets).map {
      case (startOffset, endOffset) => TextRange.create(startOffset, endOffset)
    }
    Option(Associations.collectAssociations(ranges.toSeq: _*)(scalaFile))
  }

  override def processTransferableData(
    bounds: RangeMarker,
    caretOffset: Int,
    ref: Ref[_ >: java.lang.Boolean],
    associations: Associations
  )(implicit project: Project, editor: Editor, file: ScalaFile): Unit = {
    import CodeInsightSettings._
    if (DumbService.getInstance(project).isDumb)
      return

    val setting = ScalaApplicationSettings.getInstance.ADD_IMPORTS_ON_PASTE
    if (setting == NO)
      return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    associations.restore(bounds) {
      case bindings if setting == ASK =>
        val bindingsSorted = bindings.filterNot(_.path.isEmpty).sortBy(_.path)
        if (bindingsSorted.nonEmpty) {
          val dialog = new RestoreReferencesDialog(project, bindingsSorted, file.features, editor.getColorsScheme)
          dialog.show()
          dialog.getExitCode match {
            case DialogWrapper.OK_EXIT_CODE =>
              val selectedElements = dialog.getSelectedElements
              bindingsSorted.filter(selectedElements.contains)
            case _ => Seq.empty
          }
        }
        else Seq.empty
      case bindings =>
        bindings
    }
  }
}
