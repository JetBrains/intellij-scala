package org.jetbrains.plugins.scala.codeInspection.shadowing


import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.codeInspection.shadow.PrivateShadowInspection

abstract class PrivateShadowInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[PrivateShadowInspection]

  override protected val description: String = PrivateShadowInspection.annotationDescription

  override def setUp(): Unit = {
    super.setUp()
    disableCheckingCompilerOption()
  }

  private def disableCheckingCompilerOption(): Unit = {
    val inspectionProfile = InspectionProjectProfileManager.getInstance(getProject).getCurrentProfile
    val inspectionToolWrapper = inspectionProfile.getInspectionTool("PrivateShadow", getProject)
    val inspection = inspectionToolWrapper.getTool.asInstanceOf[PrivateShadowInspection]
    inspection.setPrivateShadowCompilerOption(false)
  }
}




