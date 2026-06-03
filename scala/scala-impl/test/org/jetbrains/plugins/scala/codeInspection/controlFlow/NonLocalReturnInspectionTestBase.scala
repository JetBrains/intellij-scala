package org.jetbrains.plugins.scala.codeInspection.controlFlow

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

abstract class NonLocalReturnInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[NonLocalReturnInspection]

  override protected val description: String = NonLocalReturnInspection.annotationDescription

  protected def disableCheckingCompilerOption(): Unit = {
    val inspectionProfile = InspectionProjectProfileManager.getInstance(getProject).getCurrentProfile
    val inspectionToolWrapper = inspectionProfile.getInspectionTool("NonLocalReturn", getProject)
    val inspection = inspectionToolWrapper.getTool.asInstanceOf[NonLocalReturnInspection]
    inspection.setCheckCompilerOption(false)
  }
}
