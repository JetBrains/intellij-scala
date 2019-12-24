package org.jetbrains.plugins.scala
package scalai18n
package lang
package properties

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.psi.impl.PropertyValueImpl
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.lang.properties.{IProperty, ResourceBundleReference}
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}

/**
  * @author Ksenia.Sautina
  * @since 7/17/12
  */
final class ScalaPropertiesReferenceContributor extends PsiReferenceContributor {

  import ScalaPropertiesReferenceContributor._

  override def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit = {
    import PsiJavaPatterns._

    def literal = psiElement(classOf[ScLiteral])

    registrar.registerReferenceProvider(
      literal.andNot(psiElement(classOf[ScInterpolatedStringLiteral])),
      scalaPropertiesReferenceProvider
    )
    registrar.registerReferenceProvider(
      literal.withParent(psiNameValuePair.withName(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER)),
      resourceBundleReferenceProvider
    )

    registrar.registerReferenceProvider(
      psiElement(classOf[PropertyValueImpl]),
      propertyValueReferenceProvider
    )
  }

}

object ScalaPropertiesReferenceContributor {

  import PsiReference.EMPTY_ARRAY

  private val scalaPropertiesReferenceProvider = new PsiReferenceProvider {

    override def acceptsTarget(target: PsiElement): Boolean = target.isInstanceOf[IProperty]

    override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] =
      element match {
        case ScalaPropertyReference(reference) => Array(reference)
        case _ => EMPTY_ARRAY
      }
  }

  private val resourceBundleReferenceProvider: PsiReferenceProvider =
    (element: PsiElement, _: ProcessingContext) => Array(new ResourceBundleReference(element))

  private val propertyValueReferenceProvider: PsiReferenceProvider =
    (element: PsiElement, _: ProcessingContext) => element match {
      case JavaPropertyReferences(references) => references
      case _ => EMPTY_ARRAY
    }

  private object ScalaPropertyReference {

    def unapply(literal: ScLiteral): Option[PropertyReference] = literal match {
      case _: ScInterpolatedStringLiteral => None
      case _ if !literal.isString || literal.isMultiLineString => None
      case ScStringLiteral(text) if !text.contains(" ") => Some(new PropertyReference(text, literal, null, true))
      case _ => None
    }
  }

  private object JavaPropertyReferences {

    private val referenceProvider = {
      val provider = new JavaClassReferenceProvider
      provider.setSoft(true)
      provider
    }

    def unapply(position: PsiElement): Option[Array[PsiReference]] = position.getText.split("\\s") match {
      case Array(str) => Some(referenceProvider.getReferencesByString(str, position, 0))
      case _ => None
    }

  }

}