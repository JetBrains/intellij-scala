package org.jetbrains.plugins.scala.codeInspection


import allErrorsInspection.AnnotatorBasedErrorInspection
import caseClassParamInspection.CaseClassParamInspection
import com.intellij.codeInspection.InspectionToolProvider
import defaultFileTemplateInspection.ScalaDefaultFileTemplateUsageInspection
import deprecation.ScalaDeprecationInspection
import fileNameInspection.FileNameInspection
import inference.SupsiciousInferredTypeInspection
import org.jetbrains.plugins.scala.codeInspection.methodSignature._
import packageNameInspection.PackageNameInspection
import sugar.FunctionTupleSyntacticSugarInspection
import collection.mutable.ArrayBuffer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import typeLambdaSimplify.AppliedTypeLambdaCanBeSimplifiedInspection
import varCouldBeValInspection.VarCouldBeValInspection

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

class ScalaInspectionsProvider extends InspectionToolProvider {
  def getInspectionClasses: Array[java.lang.Class[_]] = {
    val res = new ArrayBuffer[java.lang.Class[_]]
    //todo: res += classOf[CyclicReferencesInspection]
    res += classOf[FileNameInspection]
    res += classOf[PackageNameInspection]
    res += classOf[ScalaDeprecationInspection]
    res += classOf[CaseClassParamInspection]
    res += classOf[SupsiciousInferredTypeInspection]
    //todo parser doesn't give info now to work this inspection
    //res += classOf[SuspiciousNewLineInMethodCall]
    res += classOf[VarCouldBeValInspection]
    res += classOf[FunctionTupleSyntacticSugarInspection]
    res += classOf[ScalaDefaultFileTemplateUsageInspection]
    res += classOf[AppliedTypeLambdaCanBeSimplifiedInspection]
    res += classOf[RedundantUnitTypeAnnotationInspection]
    res += classOf[MisguidingAssignmentInspection]
    res += classOf[UnitMethodWithoutParensInspection]
    res += classOf[NoParensInMethodOverrideInspection]
    res += classOf[NoParensInModifierOverrideInspection]
    res += classOf[ExcessiveParensInMemberOverrideInspection]
    res += classOf[ExcessiveParensInQueryOverrideInspection]
    res += classOf[QueryLikeMethodHasParensInspection]
    res += classOf[QueryLikeUnitMethodInspection]
    if (ApplicationManager.getApplication.asInstanceOf[ApplicationImpl].isInternal) {
      res += classOf[AnnotatorBasedErrorInspection]
    }
    res.toArray
  }
}