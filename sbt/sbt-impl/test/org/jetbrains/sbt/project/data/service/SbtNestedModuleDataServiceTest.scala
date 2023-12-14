package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.{ModuleManager, StdModuleTypes}
import org.jetbrains.annotations.Nullable
import org.jetbrains.sbt.Sbt
import com.intellij.openapi.module.Module
import org.jetbrains.sbt.project.data.{ModuleNode, NestedModuleNode}
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._

import org.junit.Assert.{assertEquals, assertTrue}

import java.io.File
import java.net.URI

class SbtNestedModuleDataServiceTest extends SbtModuleDataServiceTestCase {

  def testExternalModuleTypeSetup(): Unit = {
    val testProject = new project {
      val buildURI: URI = new File(getProject.getBasePath).toURI

      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val c1: javaModule = new javaModule {
        val moduleName = "c1"
        val uri: URI = buildURI.resolve("c1/")
        name := moduleName
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        moduleFileDirectoryPath := getProject.getBasePath + "/c1"
        externalConfigPath := getProject.getBasePath + "/c1"
        arbitraryNodes ++= Seq(
          new NestedModuleNode(
            StdModuleTypes.JAVA.getId,
            ModuleNode.combinedId("project1", Option(uri)),
            "c1.project1",
            getProject.getBasePath + "/c1/project1",
            getProject.getBasePath + "/c1/project1"
          )
        )
      }

      val root: javaModule = new javaModule {
        val moduleName = "root"
        name := moduleName
        projectId := ModuleNode.combinedId(moduleName, Option(buildURI))
        projectURI := buildURI
        moduleFileDirectoryPath := getProject.getBasePath
        externalConfigPath := getProject.getBasePath
        arbitraryNodes ++= Seq(
          new NestedModuleNode(
            StdModuleTypes.JAVA.getId,
            ModuleNode.combinedId("project1", Option(buildURI)),
            "root.project1",
            getProject.getBasePath + "/project1",
            getProject.getBasePath + "/project1"
          )
        )
      }

      modules ++= Seq(c1, root)
    }.build.toDataNode

    importProjectData(testProject)

    val moduleManager = ModuleManager.getInstance(getProject)
    val allModules = moduleManager.getModules
    val rootModules = Seq("root", "c1").map(moduleManager.findModuleByName).filter(_ != null)
    val nonRootModules = Seq("root.project1", "c1.project1").map(moduleManager.findModuleByName).filter(_ != null)

    assertTrue("The number of modules is not equal to 5", allModules.size == 5)
    assertTrue("There is less than 2 root modules", rootModules.size == 2)
    assertTrue("There is less than 2 non root modules", nonRootModules.size == 2)

    testModuleExternalType(rootModules, null)
    testModuleExternalType(nonRootModules, SbtNestedModuleDataService.sbtNestedModuleType)
  }

  private def testModuleExternalType(modules: Seq[Module], @Nullable desiredType: String): Unit = {
    modules.foreach { module =>
      val moduleExternalType = ExternalSystemModulePropertyManager.getInstance(module).getExternalModuleType
      assertEquals(desiredType, moduleExternalType)
    }
  }
}
