package org.jetbrains.plugins.scala.codeInsight.implicits

import java.awt.Insets

import com.intellij.openapi.editor.Editor

private class TextRenderer(parts: Seq[Text],
                           leftGap: Boolean, rightGap: Boolean,
                           menu: Option[String]) extends HintRendererExt(parts) {

  override def getContextMenuGroupId: String = menu.orNull

  override protected def getMargin(editor: Editor): Insets =
    new Insets(0, 0, 0, 0)

  override protected def getPadding(editor: Editor): Insets =
    new Insets(0, 0, 0, 0)
}
