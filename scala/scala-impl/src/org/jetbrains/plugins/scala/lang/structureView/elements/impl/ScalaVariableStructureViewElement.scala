package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import javax.swing.Icon

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Iconable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaVariableStructureViewElement.Presentation

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableStructureViewElement private (element: ScNamedElement, inherited: Boolean, val showType: Boolean)
  extends ScalaStructureViewElement(element, inherited) with TypedViewElement {

  override def getPresentation: ItemPresentation = new Presentation(element, inherited, showType)

  override def getChildren: Array[TreeElement] = variable match {
    case Some(definition: ScVariableDefinition) => definition.expr match {
      case Some(block: ScBlockExpr) => ScalaStructureViewElement(block).flatMap(_.getChildren).toArray
      case _ => TreeElement.EMPTY_ARRAY
    }
    case _ => TreeElement.EMPTY_ARRAY
  }

  private def variable = element.parentsInFile.findByType[ScVariable]
}

object ScalaVariableStructureViewElement {
  def apply(element: ScNamedElement, inherited: Boolean): Seq[ScalaVariableStructureViewElement] =
    Seq(//new ScalaVariableStructureViewElement(element, inherited, showType = true),
      new ScalaVariableStructureViewElement(element, inherited, showType = false))

  class Presentation(element: ScNamedElement, inherited: Boolean, showType: Boolean) extends ScalaItemPresentation(element, inherited) {

    override def location: Option[String] = variable.map(_.containingClass).map(_.name)

    override def getPresentableText: String = {
      val typeAnnotation = variable.flatMap(_.typeElement.map(_.getText))

      def inferredType = if (showType) variable.flatMap(_.`type`().toOption).map(ScTypePresentation.withoutAliases) else None

      ScalaItemPresentation.withSimpleNames(element.nameId.getText + typeAnnotation.orElse(inferredType).map(": " + _).mkString)
    }

    override def getIcon(open: Boolean): Icon =
      variable.map(_.getIcon(Iconable.ICON_FLAG_VISIBILITY)).orNull

    private def variable = element.parentsInFile.findByType[ScVariable]
  }
}