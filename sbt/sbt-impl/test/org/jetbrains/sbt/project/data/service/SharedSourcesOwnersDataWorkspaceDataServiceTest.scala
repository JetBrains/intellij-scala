package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.SbtUtil.EntityStorageOps
import org.jetbrains.sbt.WorkspaceModelUtil
import org.jetbrains.sbt.project.SharedSourcesOwnersData
import org.jetbrains.sbt.project.data.service.ExternalSystemDataDsl._
import org.jetbrains.sbt.project.data.{ModuleNode, SharedSourcesOwnersNode}
import org.jetbrains.sbt.project.sources.SharedSourcesModuleType
import org.junit.Assert.{assertEquals, assertTrue, fail}

import java.io.File
import java.net.URI
import scala.jdk.CollectionConverters.SeqHasAsJava

class SharedSourcesOwnersDataWorkspaceDataServiceTest extends SbtModuleDataServiceTestCase {

  def testSharedSourcesOwnersEntitiesExistence(): Unit = {
    val testProject = new project {
      val basePath: String = getProject.getBasePath
      val buildURI: URI = new File(basePath).toURI

      name := getProject.getName
      ideDirectoryPath := basePath
      linkedProjectPath := basePath

      val c1ModuleId: String = ModuleNode.combinedId("c1", Option(buildURI))
      val c1: javaModule = new javaModule {
        projectId := c1ModuleId
        name := "c1"
        moduleFileDirectoryPath := basePath + "/c1"
        externalConfigPath := basePath + "/c1"
      }
      val baseModuleId: String = ModuleNode.combinedId("base", Option(buildURI))
      val base: javaModule = new javaModule {
        projectId := baseModuleId
        name := "base"
        moduleFileDirectoryPath := basePath + "/base"
        externalConfigPath := basePath + "/base"
      }
      val sharedSources: sharedSourcesModule = new sharedSourcesModule {
        val sharedSourcesModuleName = s"${basePath.split('/').last}-sources"
        val moduleId: String = ModuleNode.combinedId(sharedSourcesModuleName, Option(buildURI))
        projectId := moduleId
        name := sharedSourcesModuleName
        moduleFileDirectoryPath := s"$basePath/$sharedSourcesModuleName"
        externalConfigPath := s"$basePath/$sharedSourcesModuleName"
        arbitraryNodes += new SharedSourcesOwnersNode(SharedSourcesOwnersData(Seq(baseModuleId, c1ModuleId).asJava))
      }

      val root: javaModule = new javaModule {
        val moduleName = "root"
        val moduleId: String = ModuleNode.combinedId(moduleName, Option(buildURI))
        projectId := moduleId
        name := moduleName
        moduleFileDirectoryPath := basePath
        externalConfigPath := basePath
      }

      modules ++= Seq(c1, root, sharedSources, base)
    }.build.toDataNode

    importProjectData(testProject)

    checkSharedSourcesOwnersEntities()
  }

  private def checkSharedSourcesOwnersEntities(): Unit = {
    val project = getProject
    val modules = project.modules
    val javaModules = modules.filter(ModuleType.get(_).getName == JavaModuleType.getModuleName)
    val sharedSourcesModules = modules.filter(ModuleType.get(_).getName == SharedSourcesModuleType.instance.getName)
    assertEquals("The number of java modules should be 3", 3, javaModules.size)
    assertEquals("The number of shared sources modules should be 1", 1, sharedSourcesModules.size)

    val storage = WorkspaceModel.getInstance(project).getCurrentSnapshot
    sharedSourcesModules.foreach { module =>
      val moduleName = module.getName
      val moduleEntityOpt = storage.resolveOpt(new ModuleId(moduleName))
      moduleEntityOpt.map { entity =>
        val sharedSourcesOwnersEntity = WorkspaceModelUtil.findSharedSourcesOwnersEntityForModuleEntity(entity, storage)
        assertTrue(s"There is no SharedSourcesOwnersEntity associated with module $moduleName", sharedSourcesOwnersEntity.isDefined)
      }.getOrElse {
        fail(s"There is no ModuleEntity associated with module $moduleName")
      }
    }

    javaModules.foreach { module =>
      val moduleEntityOpt = storage.resolveOpt(new ModuleId(module.getName))
      moduleEntityOpt.map { entity =>
        val sharedSourcesOwnersEntity = WorkspaceModelUtil.findSharedSourcesOwnersEntityForModuleEntity(entity, storage)
        assertTrue(s"SourcesOwnersEntity is associated with java module ${module.getName}, but it shouldn't", sharedSourcesOwnersEntity.isEmpty)
      }.getOrElse {
        fail(s"There is no ModuleEntity associated with module ${module.getName}")
      }
    }
  }
}
