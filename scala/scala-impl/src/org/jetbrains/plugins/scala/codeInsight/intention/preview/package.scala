package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.intention.preview.{IntentionPreviewInfo, IntentionPreviewUtils}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt

package object preview {

  implicit final class PreviewElementExt[T <: PsiElement](private val element: T) extends AnyVal {
    /** Could be used to create a preview for changes to another file */
    def modifyForPreview(modification: T => Unit): IntentionPreviewInfo = {
      val file = element.getContainingFile
      //noinspection ApiStatus
      val fileCopy = IntentionPreviewUtils.obtainCopyForPreview(file)
      val elementCopy = PsiTreeUtil.findSameElementInCopy(element, fileCopy)
      val document = fileCopy.getViewProvider.getDocument

      modification(elementCopy)

      val docManager = PsiDocumentManager.getInstance(element.getProject)
      docManager.doPostponedOperationsAndUnblockDocument(document)
      docManager.commitDocument(document)

      new IntentionPreviewInfo.CustomDiff(file.getFileType, file.name, file.getText, fileCopy.getText)
    }
  }

}
