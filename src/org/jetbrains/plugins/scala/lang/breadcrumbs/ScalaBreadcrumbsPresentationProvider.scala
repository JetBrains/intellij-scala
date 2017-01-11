package org.jetbrains.plugins.scala.lang.breadcrumbs

import java.awt.Color

import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager, TextAttributesKey}
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
    
    if (element.headOption.exists(!_.isInstanceOf[ScalaPsiElement])) return null
    
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
    case _: ScTemplateDefinition => getClassColor 
    case _: ScFunction => getOtherColor 
    case _: ScFunctionExpr => getOtherColor
    case _: ScalaPsiElement => getOtherColor
    case _ => getFunctionColor // why not
  }
  
  private def getClassColor = getColorByKey(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES)
  private def getFunctionColor = getColorByKey(EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES)
  private def getOtherColor = getColorByKey(EditorColors.FOLDED_TEXT_ATTRIBUTES)
  
  private def getColorByKey(attributesKey: TextAttributesKey) = 
    EditorColorsManager.getInstance.getGlobalScheme.getAttributes(attributesKey).getBackgroundColor
}
