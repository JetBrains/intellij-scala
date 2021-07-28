package org.jetbrains.plugins.scala.lang.breadcrumbs

import java.awt.Color
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager, EditorColorsScheme, TextAttributesKey}
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.xml.breadcrumbs.{BreadcrumbsPresentationProvider, CrumbPresentation}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.ui.ColorUtil._
import com.intellij.ui.{ColorUtil, JBColor}
import org.jetbrains.plugins.scala.extensions.ToNullSafe

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
  private val DEFAULT_BACKGROUND_COLOR = new JBColor(15790320, 3224373)
 

  case class MyCrumbPresentation(isTemplateDef: Boolean) extends CrumbPresentation {
    override def getBackgroundColor(selected: Boolean, hovered: Boolean, light: Boolean): Color = {
      if (hovered || selected) 
        return adjustColor (
          Option(EditorColorsManager.getInstance().getGlobalScheme.getColor(BASE_BACKGROUND_COLOR)).getOrElse(DEFAULT_BACKGROUND_COLOR), 
          if (hovered && selected) 3 else 2
        ) 
      
      val (first, second) = if (isTemplateDef) BASE_COLORS_PAIR else BASE_COLORS_PAIR.swap
      val (background1, background2) = (getBackgroundColorByKey(first), getBackgroundColorByKey(second))
      
      val color = if (background1 != null && background2 != null) 
        adjustColor(background1, background2, isTemplateDef) 
      else 
        adjustColor(getForegroundColorByKey(first), getForegroundColorByKey(second), isTemplateDef)
      
      val fgColor = getForegroundColorByKey(getBaseKey(selected, hovered, light))
      
      if (fgColor == null) color else adjustColor(color, fgColor, isTemplateDef, 4)
    }
  }
  
  case class SimpleCrumbPresentation() extends CrumbPresentation {
    override def getBackgroundColor(selected: Boolean, hovered: Boolean, light: Boolean): Color = getForegroundColorByKey(DEFAULT_COLOR)
  }
  
  private def getBaseKey(selected: Boolean, hovered: Boolean, light: Boolean) = {
    import EditorColors._
    if (selected) BREADCRUMBS_CURRENT else if (hovered) BREADCRUMBS_HOVERED else if (light) BREADCRUMBS_INACTIVE else BREADCRUMBS_DEFAULT
  }
  
  private def adjustColor(c: Color, t: Int) = if (calculateColorLuminance(c) < 0.5) brighter(c, t) else darker(c, t)
  
  private def adjustColor(c: Color, by: Color, isTemplateDef: Boolean, t: Int = 2) = {
    val (lum1, lum2) = (calculateColorLuminance(c), calculateColorLuminance(by))

    if (checkContrastRatio(lum1, lum2)) c
    else if (lum1 < lum2 || lum1 == lum2 && isTemplateDef && lum1 > MIN_LUM) darker(c, t) else brighter(c, t)
  }
  
  private def getAttributesByKey(attributesKey: TextAttributesKey): (TextAttributes, TextAttributes) = {
    val scheme = EditorColorsManager.getInstance.getGlobalScheme
    val fromGlobalScheme = scheme.getAttributes(attributesKey)
    val fallback = {
      val isDark = ColorUtil.isDark(scheme.getDefaultBackground)
      val defaultSchemeName = if (isDark) "Darcula" else EditorColorsScheme.DEFAULT_SCHEME_NAME
      EditorColorsManager.getInstance.getScheme(defaultSchemeName).getAttributes(attributesKey)
    }
    (fromGlobalScheme, fallback)
  }

  private def getForegroundColorByKey(attributesKey: TextAttributesKey) = {
    val (main, fallback) = getAttributesByKey(attributesKey)
    main.getForegroundColor.nullSafe.getOrElse(fallback.getForegroundColor)
  }
  
  private def getBackgroundColorByKey(attributesKey: TextAttributesKey) = {
    val (main, fallback) = getAttributesByKey(attributesKey)
    main.getBackgroundColor.nullSafe.getOrElse(fallback.getBackgroundColor)
  }

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
