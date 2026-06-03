// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.scala.editor.copy

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.{Editor, RawText}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

/**
 * @note JavaDoc alternative is [[com.intellij.codeInsight.editorActions.JavadocCopyPastePreProcessor]]
 */
final class ScaladocCopyPastePreProcessor extends CopyPastePreProcessor {
  @Nullable
  override def preprocessOnCopy(file: PsiFile, startOffsets: Array[Int], endOffsets: Array[Int], text: String): String = null

  @NotNull
  override def preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText): String = {
    if (!file.is[ScalaFile])
      return text

    val offset = editor.getSelectionModel.getSelectionStart
    if (DocumentUtil.isAtLineEnd(offset, editor.getDocument) && text.startsWith("\n"))
      return text

    val element = file.findElementAt(offset)
    val docComment = PsiTreeUtil.getParentOfType(element, classOf[ScDocComment], false)
    if (docComment == null)
      return text

    val document = editor.getDocument
    val lineStartOffset = DocumentUtil.getLineStartOffset(offset, document)
    val chars = document.getImmutableCharSequence
    val firstNonWsLineOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t")
    if (firstNonWsLineOffset >= offset || chars.charAt(firstNonWsLineOffset) != '*')
      return text

    val prefixWithAsterisk = chars.subSequence(lineStartOffset, firstNonWsLineOffset + 1)
    val lineStartReplacement = "\n" + prefixWithAsterisk + " "
    StringUtil.trimTrailing(text, '\n').replace("\n", lineStartReplacement)
  }
}