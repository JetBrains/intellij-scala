package org.jetbrains.plugins.hocon.ref

import com.intellij.psi.{PsiElement, PsiReference, PsiReferenceProvider}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.hocon.psi.HIncludeTarget

class IncludedFileReferenceProvider extends PsiReferenceProvider {
  def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = element match {
    case includeTarget: HIncludeTarget =>
      includeTarget.getFileReferences.asInstanceOf[Array[PsiReference]]
    case _ => PsiReference.EMPTY_ARRAY
  }
}
