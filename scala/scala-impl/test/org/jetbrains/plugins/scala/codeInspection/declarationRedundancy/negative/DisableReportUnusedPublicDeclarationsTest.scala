package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{ScalaUnusedDeclarationInspection, ScalaUnusedDeclarationInspectionTestBase}

class DisableReportUnusedPublicDeclarationsTest extends ScalaUnusedDeclarationInspectionTestBase {

  private def disableReportUnusedPublicDeclarations(): Unit = {
    val inspectionProfile = InspectionProjectProfileManager.getInstance(getProject).getCurrentProfile
    val inspectionToolWrapper = inspectionProfile.getInspectionTool("ScalaUnusedSymbol", getProject)
    val inspection = inspectionToolWrapper.getTool.asInstanceOf[ScalaUnusedDeclarationInspection]
    inspection.setReportPublicDeclarations(false)
  }

  def test_public_declarations_are_not_inspected_when_disabled(): Unit = {
    disableReportUnusedPublicDeclarations()
    checkTextHasNoErrors("class Foo { def bar() = {} }")
  }
}
