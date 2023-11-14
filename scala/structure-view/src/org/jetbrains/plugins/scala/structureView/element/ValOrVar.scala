package org.jetbrains.plugins.scala.structureView.element

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValue, ScValueOrVariable, ScVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.structureView.element.AbstractItemPresentation.withSimpleNames

import javax.swing.Icon

abstract class ValOrVar(element: ScNamedElement, private[element] val parent: ScValueOrVariable, inherited: Boolean)
  extends AbstractTreeElementDelegatingChildrenToPsi(element, inherited)
    with InheritedLocationStringItemPresentation {

  override def location: Option[String] = value.map(_.containingClass).map(_.name)

  override def getPresentableText: String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(element)
    val typeAnnotation = value.flatMap(_.typeElement.map(_.getText))
    withSimpleNames(element.name + typeAnnotation.map(": " + _).mkString)
  }

  override def getIcon(open: Boolean): Icon =
    value.map(_.getIcon(Iconable.ICON_FLAG_VISIBILITY)).orNull

  override def children: Seq[PsiElement] = {
    val definition = value match {
      case Some(definition: ScPatternDefinition)  => definition.expr
      case Some(definition: ScVariableDefinition) => definition.expr
      case _                                      => None
    }
    definition match {
      case Some(block: ScBlockExpr) => Block.childrenOf(block)
      case _                        => Seq.empty
    }
  }

  override def isAlwaysLeaf: Boolean = false

  override def getAccessLevel: Int = getAccessLevel(parent)

  private def value: Option[ScValueOrVariable] = element.parentsInFile.findByType[ScValueOrVariable]
}

final class Value(element: ScNamedElement, parent: ScValue, inherited: Boolean) extends ValOrVar(element, parent, inherited)
final class Variable(element: ScNamedElement, parent: ScVariable, inherited: Boolean) extends ValOrVar(element, parent, inherited)
