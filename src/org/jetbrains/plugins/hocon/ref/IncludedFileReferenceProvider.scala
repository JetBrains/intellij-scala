package org.jetbrains.plugins.hocon.ref

import com.intellij.psi.{PsiElement, PsiReference, PsiReferenceProvider}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.hocon.psi.HString

class IncludedFileReferenceProvider extends PsiReferenceProvider {
  def getReferencesByElement(element: PsiElement, context: ProcessingContext) = element match {
    case hs: HString if hs.isIncludeTarget =>
      hs.getFileReferences.asInstanceOf[Array[PsiReference]]
    case _ => PsiReference.EMPTY_ARRAY
  }
}
