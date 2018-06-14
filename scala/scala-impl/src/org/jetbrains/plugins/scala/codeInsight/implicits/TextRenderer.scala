package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.{Graphics, Insets, Rectangle}

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.markup.TextAttributes

private class TextRenderer(text: String, underlined: Boolean, leftGap: Boolean, rightGap: Boolean) extends HintRendererExt(text) {
  override def getContextMenuGroupId: String = "ToggleImplicits"

  override protected def getMargin(editor: Editor): Insets =
    new Insets(0, if (leftGap) 2 else 1, 0, if (rightGap) 2 else 1)

  override protected def getPadding(editor: Editor): Insets =
    new Insets(0, if (leftGap) 5 else 1, 0, if (rightGap) 5 else 2)

  // TODO Fine-grained coloring
  // TODO Why the effect type / color cannot be specified via super.getTextAttributes?
  override def paint(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes): Unit = {
    if (underlined) {
      val errorAttributes = editor.getColorsScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
      textAttributes.setEffectType(errorAttributes.getEffectType)
      textAttributes.setEffectColor(errorAttributes.getEffectColor)
    }

    super.paint(editor, g, r, textAttributes)
  }
}
