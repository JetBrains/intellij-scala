package org.jetbrains.plugins.scala
package conversion
package copy

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings
import com.intellij.psi.PsiFile

final class ScalaCopyPastePostProcessorWithRichCopySettingsAwareness extends ScalaCopyPastePostProcessor {

  override protected def collectTransferableData0(file: PsiFile,
                                                  editor: Editor,
                                                  startOffsets: Array[Int],
                                                  endOffsets: Array[Int]): Associations =
    RichCopySettings.getInstance() match {
      case copySettings if copySettings.isEnabled => super.collectTransferableData0(file, editor, startOffsets, endOffsets) // copy as plain text
      case _ => null
    }
}
