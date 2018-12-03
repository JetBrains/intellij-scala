package org.jetbrains.plugins.scala.scalai18n.lang.properties

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.psi.impl.PropertyValueImpl
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.PsiJavaPatterns._
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.injection.ScalaPatterns

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaPropertiesReferenceContributor extends PsiReferenceContributor {
  private final val CLASS_REFERENCE_PROVIDER: JavaClassReferenceProvider = new JavaClassReferenceProvider {
    override def isSoft: Boolean = {
      true
    }
  }

  def registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(ScalaPatterns.scalaLiteral().andNot(ScalaPatterns.interpolatedScalaLiteral()), new ScalaPropertiesReferenceProvider)
    registrar.registerReferenceProvider(ScalaPatterns.scalaLiteral().withParent(psiNameValuePair.withName(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)), new ResourceBundleReferenceProvider)
    registrar.registerReferenceProvider(PsiJavaPatterns.psiElement(classOf[PropertyValueImpl]), new PsiReferenceProvider {
      @NotNull def getReferencesByElement(@NotNull element: PsiElement, @NotNull context: ProcessingContext): Array[PsiReference] = {
        val text: String = element.getText
        val words: Array[String] = text.split("\\s")
        if (words.length != 1) return PsiReference.EMPTY_ARRAY
        CLASS_REFERENCE_PROVIDER.getReferencesByString(words(0), element, 0)
      }
    })
  }

}
