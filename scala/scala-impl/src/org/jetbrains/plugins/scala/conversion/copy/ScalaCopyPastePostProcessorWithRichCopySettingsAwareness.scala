package org.jetbrains.plugins.scala.conversion.copy
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings
import com.intellij.psi.PsiFile

class ScalaCopyPastePostProcessorWithRichCopySettingsAwareness extends ScalaCopyPastePostProcessor {
  override protected def collectTransferableData0(file: PsiFile, editor: Editor,
                                                  startOffsets: Array[Int], endOffsets: Array[Int]): Associations = {

    //copy as plain text
    if (!RichCopySettings.getInstance().isEnabled)
      return null

    super.collectTransferableData0(file, editor, startOffsets, endOffsets)
  }
}
