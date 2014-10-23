package org.jetbrains.plugins.scala.codeInspection.internal

import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.openapi.application.ApplicationManager

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 02.04.12
 */

class ScalaInternalInspectionsProvider extends InspectionToolProvider {
  def getInspectionClasses: Array[Class[_]] = {
    if (ApplicationManager.getApplication.isInternal) {
      val buffer = new ArrayBuffer[Class[_]]()
      buffer += classOf[ScalaWrongMethodsUsageInspection]
      buffer += classOf[AnnotatorBasedErrorInspection]
      buffer.toArray
    } else Array.empty
  }
}
