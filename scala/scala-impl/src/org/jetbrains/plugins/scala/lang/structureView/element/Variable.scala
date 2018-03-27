package org.jetbrains.plugins.scala.lang.structureView.element

import javax.swing.Icon

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.util.Iconable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

private class Variable(element: ScNamedElement, inherited: Boolean, override val showType: Boolean)
  extends AbstractTreeElement(element, inherited) with Typed {

  override def location: Option[String] = variable.map(_.containingClass).map(_.name)

  override def getPresentableText: String = {
    val typeAnnotation = variable.flatMap(_.typeElement.map(_.getText))

    def inferredType = if (showType) variable.flatMap(_.`type`().toOption).map(ScTypePresentation.withoutAliases) else None

    AbstractItemPresentation.withSimpleNames(element.nameId.getText + typeAnnotation.orElse(inferredType).map(": " + _).mkString)
  }

  override def getIcon(open: Boolean): Icon =
    variable.map(_.getIcon(Iconable.ICON_FLAG_VISIBILITY)).orNull

  override def getChildren: Array[TreeElement] = variable match {
    case Some(definition: ScVariableDefinition) => definition.expr match {
      case Some(block: ScBlockExpr) => Element(block).flatMap(_.getChildren).toArray
      case _ => TreeElement.EMPTY_ARRAY
    }
    case _ => TreeElement.EMPTY_ARRAY
  }

  private def variable = element.parentsInFile.findByType[ScVariable]
}
