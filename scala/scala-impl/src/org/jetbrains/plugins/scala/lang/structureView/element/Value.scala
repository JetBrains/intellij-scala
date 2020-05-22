package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.structureView.element.AbstractItemPresentation.withSimpleNames

/**
* @author Alexander Podkhalyuzin
* Date: 08.05.2008
*/
class Value(element: ScNamedElement, inherited: Boolean)
  extends AbstractTreeElement(element, inherited) {

  override def location: Option[String] = value.map(_.containingClass).map(_.name)

  override def getPresentableText: String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(element)
    val typeAnnotation = value.flatMap(_.typeElement.map(_.getText))
    withSimpleNames(element.name + typeAnnotation.map(": " + _).mkString)
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

  private def value = element.parentsInFile.instanceOf[ScValue]
}
