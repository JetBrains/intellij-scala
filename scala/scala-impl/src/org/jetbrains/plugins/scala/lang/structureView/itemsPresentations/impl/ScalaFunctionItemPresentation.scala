package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.ScalaItemPresentation.withSimpleNames

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionItemPresentation(element: ScFunction, inherited: Boolean, showType: Boolean)
  extends ScalaItemPresentation(element, inherited) {

  override def location: Option[String] = Option(element.containingClass).map(_.name)

  override def getPresentableText: String = {
    val presentation = ScalaElementPresentation.getMethodPresentableText(element)

    val inferredType =
      if (element.isConstructor) None
      else element.returnTypeElement match {
        case Some(_) => None
        case None => if (showType) element.returnType.toOption.map(ScTypePresentation.withoutAliases) else None
      }

    withSimpleNames(presentation + inferredType.map(": " + _).mkString)
  }

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}