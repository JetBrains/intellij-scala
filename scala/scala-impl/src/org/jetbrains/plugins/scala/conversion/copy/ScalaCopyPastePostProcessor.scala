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
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.dependency.Dependency
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.refactoring.Association
import org.jetbrains.plugins.scala.settings._

import scala.collection.{JavaConverters, mutable}

/**
  * Pavel Fatin
  */
class ScalaCopyPastePostProcessor extends SingularCopyPastePostProcessor[Associations](Associations.flavor) {

  import ScalaCopyPastePostProcessor._

  private val Log = Logger.getInstance(getClass)

  override def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                                      (implicit file: PsiFile,
                                       editor: Editor): Option[Associations] = file match {
    case scalaFile: ScalaFile if !DumbService.getInstance(scalaFile.getProject).isDumb =>
      if (startOffsets.length == 1 && startOffsets.head == 0) return None

      val ranges = startOffsets.zip(endOffsets).map {
        case (startOffset, endOffset) => TextRange.create(startOffset, endOffset)
      }

      implicit val psiFile: ScalaFile = scalaFile

      val buffer = mutable.ArrayBuffer.empty[Association]
      var result: Associations = null
      try {
        ProgressManager.getInstance().runProcess(
          (() => {
            for {
              range <- ranges
              association <- collectAssociations(range)
            } buffer += association
          }): Runnable,
          new ProgressIndicator
        )
      } catch {
        case _: ProcessCanceledException =>
          Log.warn(
            s"""Time-out while collecting dependencies in ${scalaFile.getName}:
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

      Option(result)
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

        val offset = bounds.getStartOffset

        doRestoreAssociations(value.associations, offset) {
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

  def restoreAssociations(associations: Array[Association], file: PsiFile): Unit = {
    doRestoreAssociations(associations, file.getTextRange.getStartOffset)()(file.getProject, file)
  }

  def doRestoreAssociations(associations: Seq[Association], offset: Int)
                           (filter: Seq[Binding] => Seq[Binding] = identity)
                           (implicit project: Project,
                            file: PsiFile): Unit = {
    def hasNonDefaultPackage(path: String) = path.lastIndexOf('.') match {
      case -1 => false
      case index => path.substring(0, index) match {
        case "scala" |
             "java.lang" |
             "scala.Predef" => false
        case _ => true
      }
    }

    val bindings = for {
      association <- associations
      element <- elementFor(association, file, offset)
      if !association.isSatisfiedIn(element)

      path = association.path.asString()
      if hasNonDefaultPackage(path)
    } yield Binding(element, path)

    if (bindings.isEmpty) return

    val bindingsToRestore = filter(bindings.distinctBy(_.path))

    if (bindingsToRestore.isEmpty) return

    import JavaConverters._
    val commonParent = PsiTreeUtil.findCommonParent(bindingsToRestore.map(_.element).asJava)
    val importsHolder = ScalaImportTypeFix.getImportHolder(commonParent, project)

    val paths = bindingsToRestore.map(_.path)

    inWriteAction {
      importsHolder.addImportsForPaths(paths, commonParent)
    }
  }

  def collectAssociations(range: TextRange)
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


  private def elementFor(dependency: Association, file: PsiFile, offset: Int): Option[PsiElement] = {
    val range = dependency.range.shiftRight(offset)

    for (ref <- Option(file.findElementAt(range.getStartOffset));
         parent <- ref.parent if parent.getTextRange == range) yield parent
  }

  case class Binding(element: PsiElement, path: String) {
    def importsHolder: ScImportsHolder = ScalaImportTypeFix.getImportHolder(element, element.getProject)
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
