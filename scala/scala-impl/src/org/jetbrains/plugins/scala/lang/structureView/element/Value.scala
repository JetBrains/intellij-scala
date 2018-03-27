package org.jetbrains.plugins.scala.lang.structureView.element

import javax.swing.Icon

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.structureView.element.AbstractItemPresentation.withSimpleNames

/**
* @author Alexander Podkhalyuzin
* Date: 08.05.2008
*/

private class Value(element: ScNamedElement, inherited: Boolean, override val showType: Boolean)
  extends AbstractTreeElement(element, inherited) with Typed {

  override def location: Option[String] = value.map(_.containingClass).map(_.name)

  override def getPresentableText: String = {
    val typeAnnotation = value.flatMap(_.typeElement.map(_.getText))

    def inferredType = if (showType) value.flatMap(_.`type`().toOption).map(ScTypePresentation.withoutAliases) else None

    withSimpleNames(element.name + typeAnnotation.orElse(inferredType).map(": " + _).mkString)
  }

  override def getIcon(open: Boolean): Icon =
    value.map(_.getIcon(Iconable.ICON_FLAG_VISIBILITY)).orNull

  override def children: Seq[PsiElement] = value match {
    case Some(definition: ScPatternDefinition) => definition.expr match {
      case Some(block: ScBlockExpr) => Block.childrenOf(block)
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }

  override def isAlwaysLeaf: Boolean = false

  private def value = element.parentsInFile.findByType[ScValue]
}
