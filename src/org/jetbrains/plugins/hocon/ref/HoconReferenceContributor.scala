package org.jetbrains.plugins.hocon.ref

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.{PsiReferenceContributor, PsiReferenceRegistrar}
import org.jetbrains.plugins.hocon.psi.HString

class HoconReferenceContributor extends PsiReferenceContributor {
  def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit = {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(classOf[HString]), new HStringJavaClassReferenceProvider)
  }
}
