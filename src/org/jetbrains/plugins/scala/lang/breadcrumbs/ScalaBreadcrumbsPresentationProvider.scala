package org.jetbrains.plugins.scala.lang.breadcrumbs

import java.awt.Color

import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager, TextAttributesKey}
import com.intellij.psi.PsiElement
import com.intellij.xml.breadcrumbs.{BreadcrumbsPresentationProvider, CrumbPresentation}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.ui.ColorUtil._

/**
  * User: Dmitry.Naydanov
  * Date: 24.06.16.
  */
class ScalaBreadcrumbsPresentationProvider extends BreadcrumbsPresentationProvider {
  override def getCrumbPresentations(elements: Array[PsiElement]): Array[CrumbPresentation] = {
    import ScalaBreadcrumbsPresentationProvider._
    
    if (elements.headOption.exists(!_.isInstanceOf[ScalaPsiElement])) return null
    
    elements map {
      case _: ScTemplateDefinition => MyCrumbPresentation(true)
      case _: ScFunction | _: ScFunctionExpr => MyCrumbPresentation(false)
      case _ => SimpleCrumbPresentation()
    }
  }
}

/**
  * Current API (172._+) works really weird. In `getBackgroundColor` if `hovered == false` then font color is expected; 
  * otherwise background color is expected. At the same time editor gutter color or java breadcrumbs colors taken as 
  * background/font color respectively. Thus we have to take into account "default" colors and change them slightly in 
  * order to make them look consistently.  
  */
object ScalaBreadcrumbsPresentationProvider {
  private val DEFAULT_COLOR = EditorColors.BREADCRUMBS_CURRENT
  
  private val BASE_COLORS_PAIR = (EditorColors.BREADCRUMBS_DEFAULT, EditorColors.BREADCRUMBS_HOVERED)
  private val BASE_BACKGROUND_COLOR = EditorColors.GUTTER_BACKGROUND
 

  case class MyCrumbPresentation(isTemplateDef: Boolean) extends CrumbPresentation {
    override def getBackgroundColor(selected: Boolean, hovered: Boolean, light: Boolean): Color = {
      if (hovered) {
        val c = EditorColorsManager.getInstance().getGlobalScheme.getColor(BASE_BACKGROUND_COLOR)
        val lumB = calculateColorLuminance(c)
        
        return if (lumB < 0.5) brighter(c, 2) else darker(c, 2)
      }
      
      val (first, second) = if (isTemplateDef) BASE_COLORS_PAIR else BASE_COLORS_PAIR.swap

      val (c1, c2) = (getColorByKey(first), getColorByKey(second))
      val (lum1, lum2) = (calculateColorLuminance(c1), calculateColorLuminance(c2))

      if (checkContrastRatio(lum1, lum2)) c1
        else if (lum1 < lum2 || lum1 == lum2 && isTemplateDef && lum1 > MIN_LUM) darker(c1, 2) else brighter(c1, 2)
    }
  }
  
  case class SimpleCrumbPresentation() extends CrumbPresentation {
    override def getBackgroundColor(selected: Boolean, hovered: Boolean, light: Boolean): Color = getColorByKey(DEFAULT_COLOR)
  }
  
  @inline private def getAttributesByKey(attributesKey: TextAttributesKey) = 
    EditorColorsManager.getInstance.getGlobalScheme.getAttributes(attributesKey)
  
  @inline private def getColorByKey(attributesKey: TextAttributesKey) = 
    getAttributesByKey(attributesKey).getForegroundColor

  //Good idea from Android plugin 
  private val THRESHOLD = 1.9
  private val MIN_LUM = 0.075

  private def checkContrastRatio(lum1: Double, lum2: Double) = (Math.max(lum1, lum2) + 0.05) / (Math.min(lum1, lum2) + 0.05) >= THRESHOLD
  
  private def calculateColorLuminance(color: Color) = 
    calculateLuminanceContribution(color.getRed / 255.0) * 0.2126 + 
      calculateLuminanceContribution(color.getGreen / 255.0) * 0.7152 + 
      calculateLuminanceContribution(color.getBlue / 255.0) * 0.0722

  private def calculateLuminanceContribution(colorValue: Double) = 
    if (colorValue <= 0.03928) colorValue / 12.92 else Math.pow((colorValue + 0.055) / 1.055, 2.4)
}
