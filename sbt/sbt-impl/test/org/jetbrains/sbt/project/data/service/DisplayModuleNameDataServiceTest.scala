package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.project.data.{ModuleNode, SbtDisplayModuleNameNode}
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl.{arbitraryNodes, externalConfigPath, ideDirectoryPath, javaModule, linkedProjectPath, moduleFileDirectoryPath, modules, name, project, projectId, projectURI}
import org.jetbrains.sbt.project.settings.DisplayModuleName
import org.junit.Assert.{assertEquals, fail}

import java.io.File
import java.net.URI

class DisplayModuleNameDataServiceTest extends SbtModuleDataServiceTestCase {

  def testDisplayModuleNames(): Unit = {
    val testProject = new project {
      val buildURI: URI = new File(getProject.getBasePath).toURI
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val root: javaModule = new javaModule {
        val moduleName = "root"
        name := moduleName
        projectId := ModuleNode.combinedId(moduleName, Option(buildURI))
        projectURI := buildURI
        moduleFileDirectoryPath := getProject.getBasePath
        externalConfigPath := getProject.getBasePath
        arbitraryNodes += new SbtDisplayModuleNameNode(moduleName)
      }
      val rootNestedModule: javaModule = new javaModule {
        name := "root.project1"
        projectId := ModuleNode.combinedId("project1", Option(buildURI))
        projectURI := buildURI
        moduleFileDirectoryPath := getProject.getBasePath + "/project1"
        externalConfigPath := getProject.getBasePath + "/project1"
        arbitraryNodes += new SbtDisplayModuleNameNode("project1")
      }

      modules ++= Seq(root, rootNestedModule)
    }.build.toDataNode

    importProjectData(testProject)

    val expectedDisplayModuleNames = Map(
      "root" -> "root",
      "root.project1" -> "project1"
    )
    checkDisplayModuleNames(expectedDisplayModuleNames)
  }

  private def checkDisplayModuleNames(expectedDisplayModuleNames: Map[String, String]): Unit = {
    val project = getProject
    val modules = project.modules.filter(ModuleType.get(_).getName == JavaModuleType.getModuleName)
    assertEquals("The number of modules should be 2", 2, modules.size)
    modules.foreach { module =>
      val displayName = DisplayModuleName.getInstance(module).name
      val expectedDisplayName = expectedDisplayModuleNames.get(module.getName)
      expectedDisplayName match {
        case Some(expectedName) =>
          assertEquals("The expected display module name is different than the actual one", expectedName, displayName)
        case _ => fail(s"The module name (${module.getName}) is not as expected")
      }
    }
  }
}
