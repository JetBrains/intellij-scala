package org.jetbrains.sbt.project.data.service

import org.jetbrains.sbt.project.ProjectStructureTestUtils.checkDisplayModuleNames
import org.jetbrains.sbt.project.data.{ModuleNode, SbtDisplayModuleNameNode}
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl.{arbitraryNodes, externalConfigPath, ideDirectoryPath, javaModule, linkedProjectPath, moduleFileDirectoryPath, modules, name, project, projectId, projectURI}

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
    checkDisplayModuleNames(getProject, expectedDisplayModuleNames)
  }
}
