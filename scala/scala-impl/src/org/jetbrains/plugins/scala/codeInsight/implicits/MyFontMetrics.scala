package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.font.FontRenderContext
import java.awt.{Font, FontMetrics, RenderingHints}

import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.{EditorImpl, FontInfo}
import com.intellij.util.ui.UIUtil

private class MyFontMetrics(editor: Editor, familyName: String, size: Int) {
  private val font = UIUtil.getFontWithFallback(familyName, Font.PLAIN, size)

  private val (metrics, lineHeight) = {
    val context = getCurrentContext(editor)

    (FontInfo.getFontMetrics(font, context),
      // We assume this will be a better approximation to a real line height for a given font
      Math.ceil(font.createGlyphVector(context, "Ap").getVisualBounds.getHeight).toInt)
  }

  def getFont: Font = metrics.getFont

  def getMetrics: FontMetrics = metrics

  def getLineHeight: Int = lineHeight

  def isActual(editor: Editor, familyName: String, size: Int): Boolean = {
    val font = metrics.getFont
    if (familyName != font.getFamily || size != font.getSize) {
      return false
    }
    val currentContext = getCurrentContext(editor)
    currentContext.equals(metrics.getFontRenderContext)
  }

  private def getCurrentContext(editor: Editor): FontRenderContext = {
    val editorContext = FontInfo.getFontRenderContext(editor.getContentComponent)

    new FontRenderContext(editorContext.getTransform,
      AntialiasingType.getKeyForCurrentScope(false),
      editor match {
        case impl: EditorImpl => impl.myFractionalMetricsHintValue
        case _ => RenderingHints.VALUE_FRACTIONALMETRICS_OFF
      })
  }
}
