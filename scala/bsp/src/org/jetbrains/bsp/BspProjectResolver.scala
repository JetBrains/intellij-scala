package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.Paths

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema._
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import monix.execution.{Cancelable, ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspProjectResolver._
import org.jetbrains.ide.PooledThreadExecutor
import org.langmeta.jsonrpc.Services
import org.langmeta.lsp.{LanguageClient, Window}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class BspProjectResolver extends ExternalSystemProjectResolver[BspExecutionSettings] {

  private var activeImport: Option[ActiveImport] = None

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectRootPath: String,
                                  isPreviewMode: Boolean,
                                  settings: BspExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {

    val projectRoot = new File(projectRootPath)
    val moduleFilesDirectoryPath = new File(projectRootPath, ".idea/modules").getAbsolutePath

    implicit val scheduler: Scheduler = Scheduler(PooledThreadExecutor.INSTANCE, ExecutionModel.AlwaysAsyncExecution)

    def statusUpdate(msg: String): Unit = {
      val ev = new ExternalSystemTaskNotificationEvent(id, msg)
      listener.onStatusChange(ev)
    }

    def targetsRequest(implicit client: LanguageClient) =
      endpoints.Workspace.buildTargets.request(WorkspaceBuildTargetsRequest())

    def sourcesRequest(targets: Seq[BuildTargetIdentifier])(implicit client: LanguageClient) =
      endpoints.BuildTarget.dependencySources.request(DependencySourcesParams(targets))

    def scalacOptionsRequest(targets: Seq[BuildTargetIdentifier])(implicit client: LanguageClient) =
      endpoints.BuildTarget.scalacOptions.request(ScalacOptionsParams(targets))

    def requests(implicit client: LanguageClient) =
      for {
        targetsResponse <- targetsRequest
        targets = targetsResponse.right.get.targets
        targetIds = targets.flatMap(_.id)
        sources <- sourcesRequest(targetIds)
        scalacOptions <- scalacOptionsRequest(targetIds)
      } yield {
        calculateModuleData(targets, scalacOptions.right.get.items, sources.right.get.items)
      }

    def calculateModuleData(targets: Seq[BuildTarget], optionsItems: Seq[ScalacOptionsItem], sourcesItems: Seq[DependencySourcesItem]) = {
      val targetsMap = targets.map(t => (t.id.get, t)).toMap
      val optionsMap = optionsItems.map(item => (item.target.get, item)).toMap
      val sourcesMap = sourcesItems.map(item => (item.target.get, item)).toMap

      targets.map { target =>
        val id = target.id.get
        val scalacOptions = optionsMap(id)
        val sources = sourcesMap(id)

        val sourceDirs = (for {
          src <- sources.uri
          file = src.toFileAsURI
          if file.isDirectory // TODO ignores individual source files, will not work for every build tool
        } yield file).distinct

        val classPath = scalacOptions.classpath.map(_.toFileAsURI)
        val outputPath = scalacOptions.classDirectory.toFileAsURI
        val moduleBase = commonBase(sourceDirs).get

        ModuleDescription(target, moduleBase, outputPath, sourceDirs, classPath)
      }
    }

    def createModuleNode(moduleDescription: ModuleDescription, projectNode: DataNode[ProjectData]) = {

      val contentRootData = new ContentRootData(bsp.ProjectSystemId, moduleDescription.basePath.getCanonicalPath)
      moduleDescription.sourceDirs.foreach { dir =>
        contentRootData.storePath(ExternalSystemSourceType.SOURCE, dir.getCanonicalPath)
      }

      val moduleId = moduleDescription.target.id.get.uri
      val moduleName = moduleDescription.target.displayName
      val moduleData = new ModuleData(moduleId, bsp.ProjectSystemId, StdModuleTypes.JAVA.getId, moduleName, moduleFilesDirectoryPath, projectRootPath)

      val outputPath = moduleDescription.output.getCanonicalPath
      moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, outputPath)
      moduleData.setInheritProjectCompileOutputPath(false)

      val libraryData = new LibraryData(bsp.ProjectSystemId, s"$moduleId dependencies")
      moduleDescription.classPath.foreach { path =>
        libraryData.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      val libraryDependencyData = new LibraryDependencyData(moduleData, libraryData, LibraryLevel.MODULE)


      val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)
      val libraryDependencyNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData, moduleNode)
      moduleNode.addChild(libraryDependencyNode)
      val contentRootDataNode = new DataNode[ContentRootData](ProjectKeys.CONTENT_ROOT, contentRootData, moduleNode)
      moduleNode.addChild(contentRootDataNode)

      moduleNode
    }

    val services = Services.empty
      .notification(Window.logMessage) { params => statusUpdate(params.message) }

    val projectTask = for {
      session <- BspCommunication.prepareSession(projectRoot)
      _ = statusUpdate("session prepared") // TODO remove in favor of build toolwindow nodes
      moduleDescriptions <- session.run(services, requests(_))
    } yield {

      statusUpdate("targets fetched") // TODO remove in favor of build toolwindow nodes

      val projectData = new ProjectData(bsp.ProjectSystemId, projectRoot.getName, projectRootPath, projectRootPath)
      val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)

      // synthetic root module when no natural module is at root
      val rootModule =
        if (moduleDescriptions.exists(_.basePath == projectRoot)) None
        else {
          val name = projectRoot.getName + "-root"
          val moduleData = new ModuleData(name, bsp.ProjectSystemId, BspSyntheticModuleType.Id, name, moduleFilesDirectoryPath, projectRootPath)
          val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)
          val contentRootData = new ContentRootData(bsp.ProjectSystemId, projectRoot.getCanonicalPath)
          val contentRootDataNode = new DataNode[ContentRootData](ProjectKeys.CONTENT_ROOT, contentRootData, moduleNode)
          moduleNode.addChild(contentRootDataNode)

          Some(moduleNode)
        }

      val modules = moduleDescriptions.map { moduleDescription =>
        createModuleNode(moduleDescription, projectNode)
      } ++ rootModule

      modules.foreach(m => projectNode.addChild(m))

      projectNode
    }

    statusUpdate("starting task") // TODO remove in favor of build toolwindow nodes

    val running = projectTask.runAsync
    activeImport = Some(ActiveImport(running))
    val result = Await.result(running, Duration.Inf)
    activeImport = None

    statusUpdate("finished task") // TODO remove in favor of build toolwindow nodes

    result
  }

  override def cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean =
    activeImport match {
      case Some(ActiveImport(running)) =>
        listener.beforeCancel(taskId)
        running.cancel()
        activeImport = None
        listener.onCancel(taskId)
        true // TODO this is a lie, cancel doesn't just cancel the thread
      case None =>
        false
    }

}

object BspProjectResolver {

  implicit class BspStringOps(str: String) {
    def toURI: URI = new URI(str) // TODO handle error
    def toFileAsURI: File = Paths.get(str.toURI).toFile // TODO handle error
  }

  private case class ModuleDescription(target: BuildTarget,
                                       basePath: File,
                                       output: File,
                                       sourceDirs: Seq[File],
                                       classPath: Seq[File])

  private case class ActiveImport(importCancelable: Cancelable)

  /** Find common base path of all given files */
  private def commonBase(dirs: Seq[File]) = {
    val paths = dirs.map(_.toPath)
    if (paths.isEmpty) None
    else {
      val basePath = paths.foldLeft(paths.head) { case (common, it) =>
        common.iterator().asScala.zip(it.iterator().asScala)
          .takeWhile { case (c, p) => c == p }
          .map(_._1)
          .foldLeft(paths.head.getRoot) { case (base, child) => base.resolve(child) }
      }

      Some(basePath.toFile)
    }
  }
}
