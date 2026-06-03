package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ex.ScopeToolState
import com.intellij.codeInspection.{LocalInspectionEP, LocalInspectionTool}
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import org.jetbrains.plugins.scala.base.ScalaSdkOwner

abstract class ScalaInspectionTestBase extends ScalaAnnotatorQuickFixTestBase {
  override protected def defaultVersionOverride: Option[ScalaVersion] = Some(ScalaSdkOwner.preferableSdkVersion)

  protected val classOfInspection: Class[_ <: LocalInspectionTool]

  protected override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOfInspection)
  }
}

abstract class InspectionSeverityForcingScalaInspectionTestBase extends ScalaInspectionTestBase {

  private var oldLevel: HighlightDisplayLevel = _

  protected override def setUp(): Unit = {
    super.setUp()
    val toolState = inspectionToolState
    oldLevel = toolState.getLevel
    toolState.setLevel(forcedInspectionSeverity)
  }

  override def tearDown(): Unit = {
    inspectionToolState.setLevel(oldLevel)
    super.tearDown()
  }

  private def inspectionToolState: ScopeToolState = {
    val profile = ProjectInspectionProfileManager.getInstance(myFixture.getProject).getCurrentProfile
    profile.getToolDefaultState(inspectionEP.getShortName, myFixture.getProject)
  }

  private def inspectionEP =
    LocalInspectionEP.LOCAL_INSPECTION
      .getExtensions
      .find(_.implementationClass == classOfInspection.getCanonicalName)
      .get

  protected def forcedInspectionSeverity: HighlightDisplayLevel
}