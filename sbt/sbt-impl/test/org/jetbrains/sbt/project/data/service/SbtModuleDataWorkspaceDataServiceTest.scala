package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.SbtUtil.EntityStorageOps
import org.jetbrains.sbt.project.data.{ModuleNode, SbtModuleData, SbtModuleNode}
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._
import org.junit.Assert.{assertEquals, fail}

import java.io.File
import java.net.URI
class SbtModuleDataWorkspaceDataServiceTest extends SbtModuleDataServiceTestCase {

  def testSbtModuleWSMEntitiesExistence(): Unit = {
    val testProject = new project {
      val buildURI: URI = new File(getProject.getBasePath).toURI
      val c1URI: URI = buildURI.resolve("c1/")

      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      val c1: javaModule = new javaModule {
        val moduleName = "c1"
        val moduleId: String = ModuleNode.combinedId(moduleName, Option(c1URI))
        name := moduleName
        projectId := moduleId
        projectURI := c1URI
        moduleFileDirectoryPath := getProject.getBasePath + "/c1"
        externalConfigPath := getProject.getBasePath + "/c1"
        arbitraryNodes += new SbtModuleNode(SbtModuleData(moduleId, buildURI, new File(getProject.getBasePath)))
      }
      val c1NestedModule: sbtNestedModule = new sbtNestedModule {
        val moduleId: String = ModuleNode.combinedId("project1", Option(c1URI))
        name := "c1.project1"
        projectId := moduleId
        projectURI := c1URI
        moduleFileDirectoryPath := getProject.getBasePath + "/c1/project1"
        externalConfigPath := getProject.getBasePath + "/c1/project1"
        arbitraryNodes += new SbtModuleNode(SbtModuleData(moduleId, c1URI, new File(getProject.getBasePath + "/c1/project1")))
      }

      val root: javaModule = new javaModule {
        val moduleName = "root"
        val moduleId: String = ModuleNode.combinedId(moduleName, Option(buildURI))
        name := moduleName
        projectId := moduleId
        projectURI := buildURI
        moduleFileDirectoryPath := getProject.getBasePath
        externalConfigPath := getProject.getBasePath
        arbitraryNodes += new SbtModuleNode(SbtModuleData(moduleId, buildURI, new File(getProject.getBasePath)))
      }
      val rootNestedModule: sbtNestedModule = new sbtNestedModule {
        val moduleId: String = ModuleNode.combinedId("project1", Option(buildURI))
        name := "root.project1"
        projectId := moduleId
        projectURI := buildURI
        moduleFileDirectoryPath := getProject.getBasePath + "/project1"
        externalConfigPath := getProject.getBasePath + "/project1"
        arbitraryNodes += new SbtModuleNode(SbtModuleData(moduleId, buildURI, new File(getProject.getBasePath + "/project1")))
      }

      modules ++= Seq(c1, root, rootNestedModule, c1NestedModule)
    }.build.toDataNode

    importProjectData(testProject)

    checkSbtModuleWSMEntities()
  }

  private def checkSbtModuleWSMEntities(): Unit = {
    val project = getProject
    val modules = project.modules.filter(ModuleType.get(_).getName == JavaModuleType.getModuleName)
    assertEquals("The number of modules should be 4", 4, modules.size)

    val storage = WorkspaceModel.getInstance(project).getCurrentSnapshot
    modules.foreach { module =>
      val moduleEntityOpt = storage.resolveOpt(new ModuleId(module.getName))
      moduleEntityOpt.map { entity =>
        val sbtModuleEntity = SbtUtil.findSbtModuleWSMEntityForModuleEntity(entity, storage)
        sbtModuleEntity.getOrElse {
          fail(s"There is no SbtModuleWSMEntity associated with module ${module.getName}")
        }
      }.getOrElse {
        fail(s"There is no ModuleEntity associated with module ${module.getName}")
      }
    }
  }
}
