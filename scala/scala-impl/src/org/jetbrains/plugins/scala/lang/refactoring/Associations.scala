package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, Segment, TextRange}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
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

import scala.collection.{JavaConverters, mutable}

final class Associations private(override val associations: Array[Association])
  extends AssociationsData(associations, Associations)
    with Cloneable {

  import Associations._

  override def clone(): Associations = new Associations(associations)

  override def canEqual(other: Any): Boolean = other.isInstanceOf[Associations]

  override def toString = s"Associations($associations)"

  def restore(segment: Segment)
             (filter: Seq[Binding] => Seq[Binding])
             (implicit project: Project, file: PsiFile): Unit = bindings(segment.getStartOffset) match {
    case Seq() =>
    case bindings =>
      filter(bindings.distinctBy(_.path)) match {
        case Seq() =>
        case bindingsToRestore =>
          val (elements, paths) = bindingsToRestore.unzip { binding =>
            (binding.element, binding.path)
          }

          import JavaConverters._
          val commonParent = PsiTreeUtil.findCommonParent(elements.asJava)
          val importsHolder = ScalaImportTypeFix.getImportHolder(commonParent, project)

          inWriteAction {
            importsHolder.addImportsForPaths(paths, commonParent)
          }
      }
  }

  private def bindings(offset: Int)
                      (implicit file: PsiFile) = for {
    association <- associations.toSeq
    element <- elementFor(association, offset)

    path = association.path.asString()
    if hasNonDefaultPackage(path)
  } yield Binding(element, path)
}

object Associations extends AssociationsData.Companion(classOf[Associations], "ScalaReferenceData") {

  private val logger = Logger.getInstance(getClass)

  def apply(associations: Array[Association]) = new Associations(associations)

  def unapply(associations: Associations): Some[Array[Association]] =
    Some(associations.associations)

  case class Binding(element: PsiElement, path: String)

  object Data {

    private val key = Key.create[Associations]("ASSOCIATIONS")

    def apply(element: PsiElement): Associations = element.getCopyableUserData(key)

    def update(element: PsiElement, associations: Associations): Unit = {
      element.putCopyableUserData(key, associations)
    }
  }

  def shiftFor(element: PsiElement, offsetChange: Int): Unit = Data(element) match {
    case null =>
    case Associations(associations) =>
      associations.foreach { association =>
        association.range = association.range.shiftRight(offsetChange)
      }
  }

  def restoreFor(movedElement: PsiElement): Unit = Data(movedElement) match {
    case null =>
    case associations =>
      try {
        associations.restore(movedElement.getTextRange)(identity)(movedElement.getProject, movedElement.getContainingFile)
      } finally {
        Data(movedElement) = null
      }
  }

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
        logger.warn(
          s"""Time-out while collecting dependencies in ${file.getName}:
             |${subText(ranges.head)}""".stripMargin
        )
      case e: Exception =>
        val attachments = ranges.zipWithIndex.map {
          case (range, index) => new Attachment(s"Selection-${index + 1}.scala", subText(range))
        }
        logger.error(e.getMessage, e, attachments: _*)
    } finally {
      result = Associations(buffer.toArray)
    }

    result
  }

  private class ProgressIndicator extends AbstractProgressIndicatorBase {

    import System.currentTimeMillis

    private val timeBound = currentTimeMillis + ProgressIndicator.Timeout

    override def isCanceled: Boolean = currentTimeMillis > timeBound ||
      super.isCanceled
  }

  private object ProgressIndicator {

    private val Timeout = 3000L
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

  private def hasNonDefaultPackage(path: String) = path.lastIndexOf('.') match {
    case -1 => false
    case index => path.substring(0, index) match {
      case "scala" |
           "java.lang" |
           "scala.Predef" => false
      case _ => true
    }
  }

  private def elementFor(association: Association, offset: Int)
                        (implicit file: PsiFile): Option[PsiElement] = {
    val Association(path, range) = association
    val shiftedRange = range.shiftRight(offset)

    for {
      ref <- Option(file.findElementAt(shiftedRange.getStartOffset))

      parent = ref.getParent
      if parent != null && parent.getTextRange == shiftedRange

      if !isSatisfiedIn(parent, path)
    } yield parent
  }

  import dependency._

  private def isSatisfiedIn(element: PsiElement, path: Path): Boolean = element match {
    case reference: ScReference =>
      Dependency.dependencyFor(reference).exists {
        case Dependency(_, `path`) => true
        case _ => false
      }
    case _ => false
  }

}