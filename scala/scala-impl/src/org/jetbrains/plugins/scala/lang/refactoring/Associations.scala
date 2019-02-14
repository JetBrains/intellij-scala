package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, Segment}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

import scala.collection.JavaConverters

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