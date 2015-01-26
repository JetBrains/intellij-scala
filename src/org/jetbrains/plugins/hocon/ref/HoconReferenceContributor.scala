package org.jetbrains.plugins.hocon.ref

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.{PsiReferenceContributor, PsiReferenceRegistrar}
import org.jetbrains.plugins.hocon.psi.{HIncluded, HString}

class HoconReferenceContributor extends PsiReferenceContributor {
  def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit = {
    val hStringPattern = PlatformPatterns.psiElement(classOf[HString])
    registrar.registerReferenceProvider(hStringPattern, new HStringJavaClassReferenceProvider)
    registrar.registerReferenceProvider(hStringPattern.withParent(classOf[HIncluded]), new IncludedFileReferenceProvider)
  }
}
