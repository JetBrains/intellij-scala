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
  
  def getColorFor(el: PsiElement) = el match {
    case _: ScTemplateDefinition => CLASS_COLOR //Color.CYAN
    case _: ScFunction => OTHER_COLOR //Color.GREEN
    case _: ScFunctionExpr => OTHER_COLOR //Color.LIGHT_GRAY
    case _: ScalaPsiElement => OTHER_COLOR
    case _ => null
  }
  
  private val CLASS_COLOR = EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES.getDefaultAttributes.getBackgroundColor
  private val FUNCTION_COLOR = EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES.getDefaultAttributes.getBackgroundColor
  private val OTHER_COLOR = EditorColors.FOLDED_TEXT_ATTRIBUTES.getDefaultAttributes.getBackgroundColor
}