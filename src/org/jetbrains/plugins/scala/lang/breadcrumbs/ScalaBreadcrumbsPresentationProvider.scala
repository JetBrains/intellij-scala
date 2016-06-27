package org.jetbrains.plugins.scala.lang.breadcrumbs

import java.awt.Color

import com.intellij.psi.PsiElement
import com.intellij.xml.breadcrumbs.{BreadcrumbsPresentationProvider, CrumbPresentation}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}

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
    case _: ScTemplateDefinition => Color.CYAN
    case _: ScFunctionExpr => Color.LIGHT_GRAY
    case _: ScFunction => Color.GREEN
    case _: ScMember | _: ScCaseClause => Color.LIGHT_GRAY
    case _ => Color.LIGHT_GRAY
  }
}