package org.jetbrains.plugins.scala.conversion.copy

import java.awt.datatransfer.Transferable

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil.getElementsInRange
import com.intellij.diagnostic.LogMessageEx
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ExceptionUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dependency.Dependency
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.settings._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Pavel Fatin
 */

class ScalaCopyPastePostProcessor extends SingularCopyPastePostProcessor[Associations] {
  private val Log = Logger.getInstance(getClass)
  private val Timeout = 3000L

  protected def collectTransferableData0(file: PsiFile, editor: Editor,
                                         startOffsets: Array[Int], endOffsets: Array[Int]): Associations = {
    if (DumbService.getInstance(file.getProject).isDumb) return null

    if(!file.isInstanceOf[ScalaFile]) return null

    if (startOffsets.length == 1 && startOffsets(0) == 0) return null

    val timeBound = System.currentTimeMillis + Timeout

    val buffer: mutable.Buffer[Association] = ArrayBuffer.empty
    var result: Associations = null
    try {
      ProgressManager.getInstance().runProcess(new Runnable {
        override def run(): Unit = {
          for {
            (start, end) <- startOffsets.zip(endOffsets)
            range = TextRange.create(start, end)
          } {
            collectAssociations(file, range, buffer)
          }
        }
      }, new AbstractProgressIndicatorBase {
        override def isCanceled: Boolean = {
          System.currentTimeMillis > timeBound || super.isCanceled
        }
      })
    } catch {
      case _: ProcessCanceledException =>
        Log.warn("Time-out while collecting dependencies in %s:\n%s".format(
          file.getName, file.getText.substring(startOffsets(0), endOffsets(0))))
      case e: Exception =>
        val selections = (startOffsets, endOffsets).zipped.map((a, b) => file.getText.substring(a, b))
        val attachments = selections.zipWithIndex.map(p => new Attachment(s"Selection-${p._2 + 1}.scala", p._1))
        Log.error(LogMessageEx.createEvent(e.getMessage, ExceptionUtil.getThrowableText(e), attachments: _*))
    } finally {
      result = new Associations(buffer.toVector)
    }
    result
  }

  protected def extractTransferableData0(content: Transferable): Associations = {
    content.isDataFlavorSupported(Associations.Flavor)
            .option(content.getTransferData(Associations.Flavor).asInstanceOf[Associations])
            .orNull
  }

  protected def processTransferableData0(project: Project, editor: Editor, bounds: RangeMarker,
                                         caretColumn: Int, indented: Ref[java.lang.Boolean], value: Associations) {
    if (DumbService.getInstance(project).isDumb) return

    if (ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO) return

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)

    if (!file.isInstanceOf[ScalaFile]) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val offset = bounds.getStartOffset

    doRestoreAssociations(value, file, offset, project) { bindingsToRestore =>
      if (ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
        val dialog = new RestoreReferencesDialog(project, bindingsToRestore.map(_.path.toOption.getOrElse("")).sorted.toArray)
        dialog.show()
        val selectedPahts = dialog.getSelectedElements
        if (dialog.getExitCode == DialogWrapper.OK_EXIT_CODE)
          bindingsToRestore.filter(it => selectedPahts.contains(it.path))
        else
          Seq.empty
      } else {
        bindingsToRestore
      }
    }
  }

  def restoreAssociations(value: Associations, file: PsiFile, offset: Int, project: Project) {
    doRestoreAssociations(value, file, offset, project)(identity)
  }

  private def doRestoreAssociations(value: Associations, file: PsiFile, offset: Int, project: Project)
                         (filter: Seq[Binding] => Seq[Binding]) {
    val bindings =
      (for {
        association <- value.associations
        element <- elementFor(association, file, offset)
        if !association.isSatisfiedIn(element)
      } yield {
        Binding(element, association.path.asString)
      }).filter {
        case Binding(_, path) =>
          val index = path.lastIndexOf('.')
          index != -1 && !Set("scala", "java.lang", "scala.Predef").contains(path.substring(0, index))
      }

    if (bindings.isEmpty) return

    val bindingsToRestore = filter(bindings.distinctBy(_.path))

    if (bindingsToRestore.isEmpty) return

    val commonParent = PsiTreeUtil.findCommonParent(bindingsToRestore.map(_.element).asJava)
    val importsHolder = ScalaImportTypeFix.getImportHolder(commonParent, project)

    val paths = bindingsToRestore.map(_.path)

    inWriteAction {
      importsHolder.addImportsForPaths(paths, commonParent)
    }
  }

  def collectAssociations(file: PsiFile, range: TextRange, buffer: mutable.Buffer[Association]): Unit = {
    def scopeEstimate(e: PsiElement): Option[PsiElement] = {
      e.parentsInFile
        .flatMap(_.prevSiblings)
        .collectFirst {
          case i: ScImportStmt => i
          case p: ScPackaging => p
          case cp: ScClassParents => cp
        }
    }

    def key(ref: ScReferenceElement) = (ref.refName, scopeEstimate(ref), ref.getKinds(incomplete = false))

    val allRefs = unqualifiedReferencesInRange(file, range)
    val grouped = allRefs.groupBy(key)

    for {
      refs <- grouped.values
      repr = refs.head
      dep <- Dependency.dependencyFor(repr)
      if dep.isExternal(file, range)
    } {
      refs.foreach { ref =>
        val shiftedRange = ref.getTextRange.shiftRight(-range.getStartOffset)
        buffer += Association(dep.kind, shiftedRange, dep.path)
      }
    }
  }

  private def unqualifiedReferencesInRange(file: PsiFile, range: TextRange): Seq[ScReferenceElement] = {
    getElementsInRange(file, range.getStartOffset, range.getEndOffset)
      .asScala
      .collect {
        case ref: ScReferenceExpression if isPrimary(ref) => ref
        case ref: ScStableCodeReferenceElementImpl if isPrimary(ref) => ref
      }
  }

  private def isPrimary(ref: ScReferenceElement): Boolean = {
    if (ref.qualifier.nonEmpty) return false

    ref match {
      case _: ScTypeProjection => false
      case ChildOf(sc: ScSugarCallExpr) => ref == sc.getBaseExpr
      case _ => true
    }
  }


  private def elementFor(dependency: Association, file: PsiFile, offset: Int): Option[PsiElement] = {
    val range = dependency.range.shiftRight(offset)

    for(ref <- Option(file.findElementAt(range.getStartOffset));
        parent <- ref.parent if parent.getTextRange == range) yield parent
  }

  private case class Binding(element: PsiElement, path: String) {
    def importsHolder: ScImportsHolder = ScalaImportTypeFix.getImportHolder(element, element.getProject)
  }
}
