package org.jetbrains.plugins.scala
package conversion
package copy

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dependency.Dependency
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.settings._

import scala.collection.mutable

/**
  * Pavel Fatin
  */
class ScalaCopyPastePostProcessor extends SingularCopyPastePostProcessor[Associations](Associations.flavor) {

  import ScalaCopyPastePostProcessor._

  override def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                                      (implicit file: PsiFile,
                                       editor: Editor): Option[Associations] = file match {
    case scalaFile: ScalaFile if !DumbService.getInstance(scalaFile.getProject).isDumb =>
      if (startOffsets.length == 1 && startOffsets.head == 0) return None

      val ranges = startOffsets.zip(endOffsets).map {
        case (startOffset, endOffset) => TextRange.create(startOffset, endOffset)
      }

      Option(collectAssociations(ranges: _*)(scalaFile))
    case _ => None
  }

  override def processTransferableData(bounds: RangeMarker, caretOffset: Int,
                                       ref: Ref[java.lang.Boolean], value: Associations)
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
            val dialog = new RestoreReferencesDialog(project, bindingsToRestore.map(_.path.toOption.getOrElse("")).sorted.toArray)
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

object ScalaCopyPastePostProcessor {

  private val Log = Logger.getInstance(getClass)

  def collectAssociations(ranges: TextRange*)
                         (implicit file: ScalaFile): Associations = {
    val buffer = mutable.ArrayBuffer.empty[Association]
    var result: Associations = null
    try {
      ProgressManager.getInstance().runProcess(
        (() => {
          for {
            range <- ranges
            association <- collectAssociationsForRange(range)
          } buffer += association
        }): Runnable,
        new ProgressIndicator
      )
    } catch {
      case _: ProcessCanceledException =>
        Log.warn(
          s"""Time-out while collecting dependencies in ${file.getName}:
             |${subText(ranges.head)}""".stripMargin
        )
      case e: Exception =>
        val attachments = ranges.zipWithIndex.map {
          case (range, index) => new Attachment(s"Selection-${index + 1}.scala", subText(range))
        }
        Log.error(e.getMessage, e, attachments: _*)
    } finally {
      result = Associations(buffer.toArray)
    }

    result
  }

  def collectAssociationsForRange(range: TextRange)
                                 (implicit file: ScalaFile): Iterable[Association] = {
    def scopeEstimate(e: PsiElement): Option[PsiElement] = {
      e.parentsInFile
        .flatMap(_.prevSiblings)
        .collectFirst {
          case i: ScImportStmt => i
          case p: ScPackaging => p
          case cp: ScTemplateParents => cp
        }
    }

    val groupedReferences = unqualifiedReferencesInRange(range).groupBy { ref =>
      (ref.refName, scopeEstimate(ref), ref.getKinds(incomplete = false))
    }.values

    for {
      references <- groupedReferences
      dep@Dependency(_, path) <- Dependency.dependencyFor(references.head).toSeq
      if dep.isExternal(file, range)

      reference <- references
    } yield Association(
      path,
      reference.getTextRange.shiftRight(-range.getStartOffset)
    )
  }

  private def unqualifiedReferencesInRange(range: TextRange)
                                          (implicit file: ScalaFile): Seq[ScReference] =
    file.depthFirst().filter { element =>
      range.contains(element.getTextRange)
    }.collect {
      case ref: ScReferenceExpression if isPrimary(ref) => ref
      case ref: ScStableCodeReferenceImpl if isPrimary(ref) => ref
    }.toSeq

  private def isPrimary(ref: ScReference): Boolean = {
    if (ref.qualifier.nonEmpty) return false

    ref match {
      case _: ScTypeProjection => false
      case ChildOf(sc: ScSugarCallExpr) => ref == sc.getBaseExpr
      case _ => true
    }
  }

  private def subText(range: TextRange)
                     (implicit file: ScalaFile) =
    file.getText.substring(range.getStartOffset, range.getEndOffset)

  private class ProgressIndicator extends AbstractProgressIndicatorBase {

    import System.currentTimeMillis

    private val timeBound = currentTimeMillis + ProgressIndicator.Timeout

    override def isCanceled: Boolean = currentTimeMillis > timeBound ||
      super.isCanceled
  }

  private object ProgressIndicator {

    private val Timeout = 3000L
  }

}
