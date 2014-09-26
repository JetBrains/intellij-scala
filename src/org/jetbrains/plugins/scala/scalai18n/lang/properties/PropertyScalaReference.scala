package org.jetbrains.plugins.scala
package scalai18n.lang.properties

import java.util

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.references.PropertyReference
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.{JavaPsiFacade, PsiElement}
import org.jetbrains.plugins.scala.extensions.{inReadAction, toRunnable}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaI18nUtil

import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 2014-09-25
 */
class PropertyScalaReference(key: String, element: PsiElement)
        extends PropertyReference(key, element, /*bundleName*/ null, /*soft*/ true) {

  @volatile
  private var bundleName: String = null

  @volatile
  private var soft = true

  override def isSoft: Boolean = soft
  override def setSoft(value: Boolean): Unit = soft = value

  ApplicationManager.getApplication.executeOnPooledThread {
    element match {
      case literal: ScLiteral if literal.isString =>
        val annotationParams = mutable.HashMap[String, AnyRef]()
        annotationParams += (AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER -> null)

        inReadAction {
          if (ScalaI18nUtil.mustBePropertyKey(element.getProject, literal, annotationParams)) {
            soft = false

            annotationParams.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER) match {
              case Some(resourceBundle: PsiElement) =>
                val javaPsiFacade = JavaPsiFacade.getInstance(resourceBundle.getProject)
                val bundleValue: AnyRef = javaPsiFacade.getConstantEvaluationHelper.computeConstantExpression(resourceBundle)

                if (bundleValue != null)
                  bundleName = bundleValue.toString
              case _ =>
            }
          }
        }
      case _ =>
    }
  }

  override protected def getPropertiesFiles: util.List[PropertiesFile] = {
    if (bundleName == null) null
    else retrievePropertyFilesByBundleName(bundleName, element)
  }

}
