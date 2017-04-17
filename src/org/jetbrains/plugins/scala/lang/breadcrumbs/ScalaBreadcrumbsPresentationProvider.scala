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
      element => MyCrumbPresentation(getColorKeyFor(element))
    }
  }
}

/**
  * Problem: current API doesn't allow setting color for font or border, only for background. The other colors are taken 
  * from Java settings, so if a user changes java colors, scala colors might got messed (e.g. if user sets font color to background
  * and vice versa). We could take all colors from java if all our elements were equal, but they are not. 
  * So we have to 
  *   1) get somehow different colors from Java, so if use changes them, our colors will be changed as well 
  *   2) correct them slightly if they are too similar to font color 
  */
object ScalaBreadcrumbsPresentationProvider {
  private val JAVA_HOVERED = EditorColors.BREADCRUMBS_HOVERED
  private val JAVA_CURRENT = EditorColors.BREADCRUMBS_CURRENT
  private val JAVA_DEFAULT = EditorColors.BREADCRUMBS_DEFAULT
  
  
  case class MyCrumbPresentation(colorKey: TextAttributesKey) extends CrumbPresentation {
    override def getBackgroundColor(selected: Boolean, hovered: Boolean, light: Boolean): Color = {
      import com.intellij.ui.ColorUtil._
      
      val c = getColorByKey(colorKey)
      val color = if (selected) brighter(c, 1) else if (hovered) darker(c, 1) else c

      val fontColor = getAttributesByKey(colorKey).getForegroundColor
      
      if (fontColor == null) return color
      
      val backgroundLum = calculateColorLuminance(color)
      val fontLum = calculateColorLuminance(fontColor)
      
      val contrastRatio = (Math.max(backgroundLum, fontLum) + 0.05) / (Math.min(fontLum, backgroundLum) + 0.05)
      
      if (contrastRatio < THRESHOLD) if (backgroundLum < fontLum) darker(color, 1) else brighter(color, 1) else color
    }
  }
  
  def getColorKeyFor(el: PsiElement): TextAttributesKey = el match {
    case _: ScTemplateDefinition => getClassColorKey 
    case _: ScFunction => getFunctionColorKey 
    case _: ScFunctionExpr => getFunctionColorKey
    case _: ScalaPsiElement => getOtherColorKey
    case _ => getFunctionColorKey // why not
  }
  
  private def getClassColorKey = JAVA_CURRENT
  private def getFunctionColorKey = JAVA_DEFAULT
  private def getOtherColorKey = JAVA_HOVERED
  
  @inline private def getAttributesByKey(attributesKey: TextAttributesKey) = 
    EditorColorsManager.getInstance.getGlobalScheme.getAttributes(attributesKey)
  
  @inline private def getColorByKey(attributesKey: TextAttributesKey) = 
    getAttributesByKey(attributesKey).getBackgroundColor

  
  //Good idea from Android plugin 
  private val THRESHOLD = 1.9

  private def calculateColorLuminance(color: Color) = 
    calculateLuminanceContribution(color.getRed / 255.0) * 0.2126 + 
      calculateLuminanceContribution(color.getGreen / 255.0) * 0.7152 + 
      calculateLuminanceContribution(color.getBlue / 255.0) * 0.0722

  private def calculateLuminanceContribution(colorValue: Double) = 
    if (colorValue <= 0.03928) colorValue / 12.92 else Math.pow((colorValue + 0.055) / 1.055, 2.4)
}
