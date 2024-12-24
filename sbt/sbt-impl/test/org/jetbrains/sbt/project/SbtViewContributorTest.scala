package org.jetbrains.sbt.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.view.ExternalSystemViewContributor
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase.{assertEquals, fail}
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ModuleDependencyData}

class SbtViewContributorTest extends LightPlatformTestCase {

  private var sbtViewContributor: SbtViewContributor = _

  override def setUp(): Unit = {
    super.setUp()
    val area = ApplicationManager.getApplication.getExtensionArea
    val extensionPoint = area.getExtensionPoint(ExternalSystemViewContributor.EP_NAME)
    val sbtExternalViewContributor = extensionPoint.getExtensions.toSeq.collectFirst { case x: SbtViewContributor => x }.orNull
    if (sbtExternalViewContributor != null) {
      sbtViewContributor = sbtExternalViewContributor
    } else {
      fail("Cannot find an instance of SbtViewContributor")
    }
  }

  def testDisplayModuleNameForModuleDependencyData(): Unit = {
    val targetInternalModuleName = "root.module1"
    val targetModuleData = new ModuleData("moduleId1", SbtProjectSystem.Id, "typeId", "m1", "1", "1-ext")
    targetModuleData.setInternalName(targetInternalModuleName)
    val ownerModuleData = new ModuleData("moduleId2", SbtProjectSystem.Id, "typeId", "m2", "2", "2-ext")

    val moduleDependencyData = new ModuleDependencyData(ownerModuleData, targetModuleData)
    val moduleDependencyDataNode = new DataNode(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData, null)
    val actual = sbtViewContributor.getDisplayName(moduleDependencyDataNode)
    assertEquals(s"The display module name should be $targetInternalModuleName, but was $actual", targetInternalModuleName, actual)
  }
}
