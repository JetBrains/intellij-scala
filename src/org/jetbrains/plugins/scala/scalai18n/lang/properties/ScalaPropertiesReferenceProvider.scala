package org.jetbrains.plugins.scala.scalai18n.lang.properties

import com.intellij.psi._
import com.intellij.lang.properties.IProperty
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import collection.mutable
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

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
    var value: AnyRef = null
    var bundleName: String = null
    var soft: Boolean = myDefaultSoft
    element match {
      case literalExpression: ScLiteral =>
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
      case _ =>
    }
    value match {
      case text: String =>
        val reference: PsiReference = new PropertyReference(text, element, bundleName, soft)
        return Array[PsiReference](reference)
      case _ =>
    }
    PsiReference.EMPTY_ARRAY
  }
}
