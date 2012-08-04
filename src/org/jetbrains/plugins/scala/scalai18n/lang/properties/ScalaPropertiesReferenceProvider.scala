package org.jetbrains.plugins.scala.scalai18n.lang.properties

import com.intellij.psi._
import com.intellij.lang.properties.IProperty
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import collection.mutable
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil
import org.jetbrains.annotations.NotNull

/**
 * @author Ksenia.Sautina
 * @since 7/17/12
 */

class ScalaPropertiesReferenceProvider(myDefaultSoft: Boolean) extends PsiReferenceProvider {
  override def acceptsTarget(@NotNull target: PsiElement): Boolean = {
    target.isInstanceOf[IProperty]
  }

  @NotNull def getReferencesByElement(@NotNull element: PsiElement, @NotNull context: ProcessingContext): Array[PsiReference] = {
    var value: AnyRef = null
    var bundleName: String = null
    var soft: Boolean = myDefaultSoft
    if (element.isInstanceOf[ScLiteral]) {
      val literalExpression: ScLiteral = element.asInstanceOf[ScLiteral]
      value = literalExpression.getValue
      val annotationParams = new mutable.HashMap[String, AnyRef]
      annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null)
      if (ScalaI18nUtil.mustBePropertyKey(element.getProject, literalExpression, annotationParams)) {
        soft = false
        val resourceBundleName = annotationParams.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER).getOrElse(null)
        if (resourceBundleName != null && resourceBundleName.isInstanceOf[PsiExpression]) {
          val expr: PsiExpression = resourceBundleName.asInstanceOf[PsiExpression]
          val bundleValue: AnyRef = JavaPsiFacade.getInstance(expr.getProject).getConstantEvaluationHelper.computeConstantExpression(expr)
          bundleName = if (bundleValue == null) null else bundleValue.toString
        }
      }
    }
    if (value.isInstanceOf[String]) {
      val text: String = value.asInstanceOf[String]
      val reference: PsiReference = new PropertyReference(text, element, bundleName, soft)
      return Array[PsiReference](reference)
    }
    PsiReference.EMPTY_ARRAY
  }
}
