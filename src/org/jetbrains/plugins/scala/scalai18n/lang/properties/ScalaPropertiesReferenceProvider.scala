package org.jetbrains.plugins.scala.scalai18n.lang.properties

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaPropertiesReferenceProvider(myDefaultSoft: Boolean) extends PsiReferenceProvider {
  override def acceptsTarget(target: PsiElement): Boolean = {
    target.isInstanceOf[IProperty]
  }

  def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    if (ScalaProjectSettings.getInstance(element.getProject).isDisableI18N) return Array.empty

    def canBeKey(literal: ScLiteral) = literal.isString && (literal.getValue match {
      case str: String => !str.contains(" ")
      case _ => false
    })

    element match {
      case stringLiteral: ScLiteral if canBeKey(stringLiteral) =>
        val litValue = stringLiteral.getValue.asInstanceOf[String]
        val annotationParams = mutable.HashMap[String, AnyRef](AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER -> null)

        if (!ScalaI18nUtil.mustBePropertyKey(element.getProject, stringLiteral, annotationParams)) return PsiReference.EMPTY_ARRAY

        annotationParams get AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER collect {
          case resourceBundleName: PsiElement =>
            val bundleValue: AnyRef = JavaPsiFacade.getInstance(
              resourceBundleName.getProject).getConstantEvaluationHelper.computeConstantExpression(resourceBundleName)
            val bundleName = if (bundleValue == null) null else bundleValue.toString
            Array[PsiReference](new PropertyReference(litValue, stringLiteral, bundleName, false))
        } getOrElse PsiReference.EMPTY_ARRAY

      case _ => PsiReference.EMPTY_ARRAY
    }

  }
}
