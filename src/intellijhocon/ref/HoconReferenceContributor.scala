package intellijhocon
package ref

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.{PsiReferenceContributor, PsiReferenceRegistrar}
import psi.HString

class HoconReferenceContributor extends PsiReferenceContributor {
  def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit = {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(classOf[HString]), new HStringJavaClassReferenceProvider)
  }
}
