package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaFunctionStructureViewElement.Presentation

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionStructureViewElement private (function: ScFunction, inherited: Boolean, val showType: Boolean)
  extends ScalaStructureViewElement(function, inherited) with TypedViewElement {

  override def getPresentation: ItemPresentation = new Presentation(function, inherited, showType)

  override def getChildren: Array[TreeElement] = function match {
    case definition: ScFunctionDefinition => definition.body match {
      case Some(block: ScBlockExpr) => new ScalaBlockStructureViewElement(block).getChildren
      case _ => Array.empty
    }
    case _ => Array.empty
  }
}

object ScalaFunctionStructureViewElement {
  def apply(function: ScFunction, inherited: Boolean): Seq[ScalaFunctionStructureViewElement] =
    Seq(//new ScalaFunctionStructureViewElement(function, isInherited, showType = true),
      new ScalaFunctionStructureViewElement(function, inherited, showType = false))

  class Presentation(element: ScFunction, inherited: Boolean, showType: Boolean) extends ScalaItemPresentation(element, inherited) {
    override def location: Option[String] = Option(element.containingClass).map(_.name)

    override def getPresentableText: String = {
      val presentation = ScalaElementPresentation.getMethodPresentableText(element)

      val inferredType =
        if (element.isConstructor) None
        else element.returnTypeElement match {
          case Some(_) => None
          case None => if (showType) element.returnType.toOption.map(ScTypePresentation.withoutAliases) else None
        }

      ScalaItemPresentation.withSimpleNames(presentation + inferredType.map(": " + _).mkString)
    }

    override def getTextAttributesKey: TextAttributesKey =
      if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
  }
}