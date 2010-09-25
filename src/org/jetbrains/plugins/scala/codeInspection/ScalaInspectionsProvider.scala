package org.jetbrains.plugins.scala
package codeInspection


import allErrorsInspection.AnnotatorBasedErrorInspection
import caseClassParamInspection.CaseClassParamInspection
import com.intellij.codeInspection.InspectionToolProvider
import com.intellij.openapi.components.ApplicationComponent
import deprecation.ScalaDeprecationInspection
import fileNameInspection.FileNameInspection
import inference.SupsiciousInferredTypeInspection
import java.lang.String
import packageNameInspection.PackageNameInspection
import referenceInspections.CyclicReferencesInspection
import sugar.FunctionTupleSyntacticSugarInspection
import collection.mutable.ArrayBuffer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import varCouldBeValInspection.VarCouldBeValInspection

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

class ScalaInspectionsProvider extends InspectionToolProvider with ApplicationComponent {
  def getInspectionClasses: Array[java.lang.Class[_]] = {
    val res = new ArrayBuffer[java.lang.Class[_]]
    //todo: res += classOf[CyclicReferencesInspection]
    res += classOf[FileNameInspection]
    res += classOf[PackageNameInspection]
    res += classOf[ScalaDeprecationInspection]
    res += classOf[CaseClassParamInspection]
    res += classOf[SupsiciousInferredTypeInspection]
    res += classOf[SuspiciousNewLineInMethodCall]
    res += classOf[VarCouldBeValInspection]
    res += classOf[FunctionTupleSyntacticSugarInspection]
    if (ApplicationManager.getApplication.asInstanceOf[ApplicationImpl].isInternal) {
      res += classOf[AnnotatorBasedErrorInspection]
    }
    res.toArray
  }

  def initComponent: Unit = {}

  def disposeComponent: Unit = {}

  def getComponentName: String = "Scala Inspections Provider"
}