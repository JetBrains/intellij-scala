package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.module.{ModuleManager, StdModuleTypes}
import org.jetbrains.plugins.scala.util.SbtModuleType.{sbtNestedModuleType, sbtSourceSetModuleType}
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._
import org.jetbrains.sbt.project.data.{ModuleNode, NestedModuleNode, SbtSourceSetModuleNode}
import org.junit.Assert.assertTrue

import java.io.File
import java.net.URI

class SbtSourceSetModuleDataServiceTest extends SbtModuleDataServiceTestCase {

  def testExternalModuleTypeSetup(): Unit = {
    val testProject = new project {
      val buildURI: URI = new File(getProject.getBasePath).toURI
      val c1URI: URI = buildURI.resolve("c1/")

      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val c1NestedSourceSetModuleNodes: Seq[SbtSourceSetModuleNode] = Seq(
        new SbtSourceSetModuleNode(
          StdModuleTypes.JAVA.getId,
          ModuleNode.combinedId("project1:main", Option(c1URI)),
          "c1.project1.main",
          getProject.getBasePath + "/c1/project1",
          getProject.getBasePath + "/c1/project1"
        ),
        new SbtSourceSetModuleNode(
          StdModuleTypes.JAVA.getId,
          ModuleNode.combinedId("project1:test", Option(c1URI)),
          "c1.project1.test",
          getProject.getBasePath + "/c1/project1",
          getProject.getBasePath + "/c1/project1"
        )
      )

      val c1NestedModuleNode: NestedModuleNode = new NestedModuleNode(
        StdModuleTypes.JAVA.getId,
        ModuleNode.combinedId("project1", Option(c1URI)),
        "c1.project1",
        getProject.getBasePath + "/c1/project1",
        getProject.getBasePath + "/c1/project1"
      )
      c1NestedModuleNode.addAll(c1NestedSourceSetModuleNodes)

      val c1: javaModule = new javaModule {
        val moduleName = "c1"
        name := moduleName
        projectId := ModuleNode.combinedId(moduleName, Option(c1URI))
        projectURI := c1URI
        moduleFileDirectoryPath := getProject.getBasePath + "/c1"
        externalConfigPath := getProject.getBasePath + "/c1"
        arbitraryNodes ++= Seq(
          new SbtSourceSetModuleNode(
            StdModuleTypes.JAVA.getId,
            ModuleNode.combinedId("c1:main", Option(c1URI)),
            "c1.main",
            getProject.getBasePath + "/c1",
            getProject.getBasePath + "/c1"
          ),
          new SbtSourceSetModuleNode(
            StdModuleTypes.JAVA.getId,
            ModuleNode.combinedId("c1:test", Option(c1URI)),
            "c1.test",
            getProject.getBasePath + "/c1",
            getProject.getBasePath + "/c1"
          )
        ) :+ c1NestedModuleNode
      }

      val root: javaModule = new javaModule {
        val moduleName = "root"
        name := moduleName
        projectId := ModuleNode.combinedId(moduleName, Option(buildURI))
        projectURI := buildURI
        moduleFileDirectoryPath := getProject.getBasePath
        externalConfigPath := getProject.getBasePath
        arbitraryNodes ++= Seq(
          new SbtSourceSetModuleNode(
            StdModuleTypes.JAVA.getId,
            ModuleNode.combinedId("root:main", Option(buildURI)),
            "root.main",
            getProject.getBasePath,
            getProject.getBasePath
          ),
          new SbtSourceSetModuleNode(
            StdModuleTypes.JAVA.getId,
            ModuleNode.combinedId("root:test", Option(buildURI)),
            "root.test",
            getProject.getBasePath,
            getProject.getBasePath
          )
        )
      }

      modules ++= Seq(c1, root)
    }.build.toDataNode

    importProjectData(testProject)

    val moduleManager = ModuleManager.getInstance(getProject)
    val allModules = moduleManager.getModules
    val rootModules = Seq("root", "c1").map(moduleManager.findModuleByName).filter(_ != null)
    val nestedModules = Seq("c1.project1").map(moduleManager.findModuleByName).filter(_ != null)
    val sbtSourceSetModules = Seq(
      "root.main", "root.test",
      "c1.main", "c1.test", "c1.project1.main", "c1.project1.test"
    ).map(moduleManager.findModuleByName).filter(_ != null)

    assertTrue("The number of modules is not equal to 10", allModules.size == 10)
    assertTrue("The number of root modules is not equal to 2", rootModules.size == 2)
    assertTrue("The number of nested modules is not equal to 1", nestedModules.size == 1)
    assertTrue("The number of sbt source set modules is not equal to 6", sbtSourceSetModules.size == 6)

    testModuleExternalType(rootModules, null)
    testModuleExternalType(nestedModules, sbtNestedModuleType)
    testModuleExternalType(sbtSourceSetModules, sbtSourceSetModuleType)
  }
}
