package org.jetbrains.plugins.scala.lang.breadcrumbs

import java.awt.Color

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.xml.breadcrumbs.{BreadcrumbsPresentationProvider, CrumbPresentation}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * User: Dmitry.Naydanov
  * Date: 24.06.16.
  */
class ScalaBreadcrumbsPresentationProvider extends BreadcrumbsPresentationProvider {
  override def getCrumbPresentations(element: Array[PsiElement]): Array[CrumbPresentation] = {
    import ScalaBreadcrumbsPresentationProvider._
    
    element.map {
      element => MyCrumbPresentation(getColorFor(element))
    }
  }
}

object ScalaBreadcrumbsPresentationProvider {
  case class MyCrumbPresentation(color: Color) extends CrumbPresentation {
    override def getBackgroundColor(selected: Boolean, hovered: Boolean, light: Boolean): Color = color
  }
  
  def getColorFor(el: PsiElement): Color = el match {
    case _: ScTemplateDefinition => getClassColor //Color.CYAN
    case _: ScFunction => getOtherColor //Color.GREEN
    case _: ScFunctionExpr => getOtherColor //Color.LIGHT_GRAY
    case _: ScalaPsiElement => getOtherColor
    case _ => null
  }
  
  private def getClassColor = EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES.getDefaultAttributes.getBackgroundColor
  private def getFunctionColor = EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES.getDefaultAttributes.getBackgroundColor
  private def getOtherColor = EditorColors.FOLDED_TEXT_ATTRIBUTES.getDefaultAttributes.getBackgroundColor
}