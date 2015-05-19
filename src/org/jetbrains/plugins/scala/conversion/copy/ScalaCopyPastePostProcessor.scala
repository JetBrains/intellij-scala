package org.jetbrains.plugins.scala.conversion.copy

import java.awt.datatransfer.Transferable
import java.lang.Boolean

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import com.intellij.diagnostic.LogMessageEx
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import com.intellij.util.ExceptionUtil
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dependency.Dependency
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.settings._

import scala.collection.JavaConversions._
import scala.util.control.Breaks._

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

    val timeBound = System.currentTimeMillis + Timeout

    var associations: List[Association] = Nil

    try {
      ProgressManager.getInstance().runProcess(new Runnable {
        override def run(): Unit = {
          breakable {
            for ((startOffset, endOffset) <- startOffsets.zip(endOffsets);
                 element <- CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
                 reference <- element.asOptionOf[ScReferenceElement];
                 dependency <- Dependency.dependencyFor(reference) if dependency.isExternal;
                 range = dependency.source.getTextRange.shiftRight(-startOffset)) {
              if (System.currentTimeMillis > timeBound) {
                Log.warn("Time-out while collecting dependencies in %s:\n%s".format(
                  file.getName, file.getText.substring(startOffset, endOffset)))
                break()
              }
              associations ::= Association(dependency.kind, range, dependency.path)
            }
          }
        }
      }, new AbstractProgressIndicatorBase {
        override def isCanceled: scala.Boolean = {
          System.currentTimeMillis > timeBound || super.isCanceled
        }
      })
    } catch {
      case p: ProcessCanceledException =>
        Log.warn("Time-out while collecting dependencies in %s:\n%s".format(
          file.getName, file.getText.substring(startOffsets(0), endOffsets(0))))
      case e: Exception =>
        val selections = (startOffsets, endOffsets).zipped.map((a, b) => file.getText.substring(a, b))
        val attachments = selections.zipWithIndex.map(p => new Attachment(s"Selection-${p._2 + 1}.scala", p._1))
        Log.error(LogMessageEx.createEvent(e.getMessage, ExceptionUtil.getThrowableText(e), attachments: _*))
    }
    new Associations(associations.reverse)
  }

  protected def extractTransferableData0(content: Transferable) = {
    content.isDataFlavorSupported(Associations.Flavor)
            .ifTrue(content.getTransferData(Associations.Flavor).asInstanceOf[Associations])
            .orNull
  }

  protected def processTransferableData0(project: Project, editor: Editor, bounds: RangeMarker,
                                         caretColumn: Int, indented: Ref[Boolean], value: Associations) {
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
      } yield Binding(element, association.path.asString(ScalaCodeStyleSettings.getInstance(project).
                isImportMembersUsingUnderScore))).filter {
        case Binding(_, path) =>
          val index = path.lastIndexOf('.')
          index != -1 && !Set("scala", "java.lang", "scala.Predef").contains(path.substring(0, index))
      }

    if (bindings.isEmpty) return

    val bindingsToRestore = filter(bindings.distinctBy(_.path))

    if (bindingsToRestore.isEmpty) return

    inWriteAction {
      for (Binding(ref, path) <- bindingsToRestore;
           holder = ScalaImportTypeFix.getImportHolder(ref, file.getProject))
        holder.addImportForPath(path, ref)
    }
  }

  private def elementFor(dependency: Association, file: PsiFile, offset: Int): Option[PsiElement] = {
    val range = dependency.range.shiftRight(offset)

    for(ref <- Option(file.findElementAt(range.getStartOffset));
        parent <- ref.parent if parent.getTextRange == range) yield parent
  }

  private case class Binding(element: PsiElement, path: String)
}