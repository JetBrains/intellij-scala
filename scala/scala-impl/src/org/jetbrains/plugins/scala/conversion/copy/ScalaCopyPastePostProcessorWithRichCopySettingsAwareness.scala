package org.jetbrains.plugins.scala
package conversion
package copy

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings
import com.intellij.psi.PsiFile

final class ScalaCopyPastePostProcessorWithRichCopySettingsAwareness extends ScalaCopyPastePostProcessor {

  override def collectTransferableData(startOffsets: Array[Int], endOffsets: Array[Int])
                                      (implicit file: PsiFile, editor: Editor): Option[lang.refactoring.Associations] =
    RichCopySettings.getInstance() match {
      case copySettings if copySettings.isEnabled => super.collectTransferableData(startOffsets, endOffsets) // copy as plain text
      case _ => None
    }
}
