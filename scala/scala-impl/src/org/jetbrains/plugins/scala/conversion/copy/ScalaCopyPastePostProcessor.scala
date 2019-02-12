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
  private val Log = Logger.getInstance(getClass)
  private val Timeout = 3000L

  override def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                                      (implicit file: PsiFile, editor: Editor): Option[Associations] = {
    if (DumbService.getInstance(file.getProject).isDumb) return None

    if (!file.isInstanceOf[ScalaFile]) return None

    if (startOffsets.length == 1 && startOffsets(0) == 0) return None

    val timeBound = System.currentTimeMillis + Timeout

    val buffer = mutable.ArrayBuffer.empty[Association]
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
        Log.error(e.getMessage, e, attachments: _*)
    } finally {
      result = Associations(buffer.toArray)
    }
    Option(result)
  }

  override def processTransferableData(bounds: RangeMarker, caretOffset: Int,
                                       ref: Ref[java.lang.Boolean], value: Associations)
                                      (implicit project: Project,
                                       editor: Editor,
                                       file: ScalaFile): Unit = {
    if (DumbService.getInstance(project).isDumb) return

    val settings = ScalaApplicationSettings.getInstance()
    if (settings.ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val offset = bounds.getStartOffset

    doRestoreAssociations(value.associations, file, offset, project) { bindingsToRestore =>
      if (settings.ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
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

  def restoreAssociations(value: Associations, file: PsiFile, offset: Int, project: Project): Unit = {
    doRestoreAssociations(value.associations, file, offset, project)()
  }

  def doRestoreAssociations(associations: Seq[Association], file: PsiFile, offset: Int, project: Project)
                           (filter: Seq[Binding] => Seq[Binding] = identity): Unit = {
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

  def collectAssociations(file: PsiFile, range: TextRange, buffer: mutable.Buffer[Association]): Unit = {
    def scopeEstimate(e: PsiElement): Option[PsiElement] = {
      e.parentsInFile
        .flatMap(_.prevSiblings)
        .collectFirst {
          case i: ScImportStmt => i
          case p: ScPackaging => p
          case cp: ScTemplateParents => cp
        }
    }

    def key(ref: ScReference) = (ref.refName, scopeEstimate(ref), ref.getKinds(incomplete = false))

    val allRefs = unqualifiedReferencesInRange(file, range)
    val grouped = allRefs.groupBy(key)

    for {
      refs <- grouped.values
      repr = refs.head
      dep@Dependency(_, path) <- Dependency.dependencyFor(repr)
      if dep.isExternal(file, range)
    } {
      refs.map {
        _.getTextRange.shiftRight(-range.getStartOffset)
      }.foreach { shiftedRange =>
        buffer += Association(path, shiftedRange)
      }
    }
  }

  private def unqualifiedReferencesInRange(file: PsiFile, range: TextRange): Seq[ScReference] = {
    file.depthFirst().filter { elem =>
      range.contains(elem.getTextRange)
    }.collect {
      case ref: ScReferenceExpression if isPrimary(ref) => ref
      case ref: ScStableCodeReferenceImpl if isPrimary(ref) => ref
    }.toVector
  }

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

}
