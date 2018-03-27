package org.jetbrains.plugins.scala.lang.structureView.element

import javax.swing.Icon

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.util.Iconable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation

/**
* @author Alexander Podkhalyuzin
* Date: 08.05.2008
*/
// TODO Should be private
class ScalaValueStructureViewElement(element: ScNamedElement, inherited: Boolean, val showType: Boolean)
  extends ScalaStructureViewElement(element, inherited) with TypedViewElement {

  override def location: Option[String] = value.map(_.containingClass).map(_.name)

  override def getPresentableText: String = {
    val typeAnnotation = value.flatMap(_.typeElement.map(_.getText))

    def inferredType = if (showType) value.flatMap(_.`type`().toOption).map(ScTypePresentation.withoutAliases) else None

    ScalaItemPresentation.withSimpleNames(element.name + typeAnnotation.orElse(inferredType).map(": " + _).mkString)
  }

  override def getIcon(open: Boolean): Icon =
    value.map(_.getIcon(Iconable.ICON_FLAG_VISIBILITY)).orNull

  override def getChildren: Array[TreeElement] = value match {
    case Some(definition: ScPatternDefinition) => definition.expr match {
      case Some(block: ScBlockExpr) => ScalaStructureViewElement(block).flatMap(_.getChildren).toArray
      case _ => TreeElement.EMPTY_ARRAY
    }
    case _ => TreeElement.EMPTY_ARRAY
  }

  private def value = element.parentsInFile.findByType[ScValue]
}
