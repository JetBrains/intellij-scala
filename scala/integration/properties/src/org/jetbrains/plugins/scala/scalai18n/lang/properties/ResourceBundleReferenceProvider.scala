package org.jetbrains.plugins.scala.scalai18n.lang.properties

import com.intellij.lang.properties.ResourceBundleReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext

//com.intellij.lang.properties.ResourceBundleReferenceProvider works with UAST since 2019.1
class ResourceBundleReferenceProvider extends PsiReferenceProvider {

  override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    val reference = new ResourceBundleReference(element)
    Array[PsiReference](reference)
  }
}
