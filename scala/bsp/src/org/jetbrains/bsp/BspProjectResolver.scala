package org.jetbrains.bsp

import java.io.File
import java.net.URI

import ch.epfl.scala.bsp.endpoints
import ch.epfl.scala.bsp.schema._
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import monix.execution.{Cancelable, ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspProjectResolver._
import org.jetbrains.bsp.BspUtil._
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

    def requests(implicit client: LanguageClient) = {
      import endpoints._
      for {
        targetsResponse <- Workspace.buildTargets.request(WorkspaceBuildTargetsRequest())
        targets = targetsResponse.right.get.targets
        targetIds = targets.flatMap(_.id)
        sources <- BuildTarget.dependencySources.request(DependencySourcesParams(targetIds))
        scalacOptions <- BuildTarget.scalacOptions.request(ScalacOptionsParams(targetIds))
      } yield {
        calculateModuleDescription(targets, scalacOptions.right.get.items, sources.right.get.items)
      }
    }

    def createModuleNode(moduleDescription: ModuleDescription, projectNode: DataNode[ProjectData]) = {

      val contentRootData = new ContentRootData(bsp.ProjectSystemId, moduleDescription.basePath.getCanonicalPath)
      moduleDescription.sourceDirs.foreach { dir =>
        contentRootData.storePath(ExternalSystemSourceType.SOURCE, dir.getCanonicalPath)
      }
      moduleDescription.testSourceDirs.foreach { dir =>
        contentRootData.storePath(ExternalSystemSourceType.TEST, dir.getCanonicalPath)
      }

      val primaryTarget = moduleDescription.targets.head
      val moduleId = primaryTarget.id.get.uri
      val moduleName = primaryTarget.displayName
      val moduleData = new ModuleData(moduleId, bsp.ProjectSystemId, StdModuleTypes.JAVA.getId, moduleName, moduleFilesDirectoryPath, projectRootPath)

      moduleDescription.output.foreach { outputPath =>
        moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, outputPath.getCanonicalPath)
      }
      moduleDescription.testOutput.foreach { outputPath =>
        moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, outputPath.getCanonicalPath)
      }

      moduleData.setInheritProjectCompileOutputPath(false)

      val libraryData = new LibraryData(bsp.ProjectSystemId, s"$moduleName dependencies")
      moduleDescription.classPath.foreach { path =>
        libraryData.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      val libraryDependencyData = new LibraryDependencyData(moduleData, libraryData, LibraryLevel.MODULE)
      libraryDependencyData.setScope(DependencyScope.COMPILE)

      val libraryTestData = new LibraryData(bsp.ProjectSystemId, s"$moduleName test dependencies")
      moduleDescription.testClassPath.foreach { path =>
        libraryTestData.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      val libraryTestDependencyData = new LibraryDependencyData(moduleData, libraryTestData, LibraryLevel.MODULE)
      libraryTestDependencyData.setScope(DependencyScope.TEST)


      val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)

      val libraryDependencyNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData, moduleNode)
      moduleNode.addChild(libraryDependencyNode)
      val libraryTestDependencyNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryTestDependencyData, moduleNode)
      moduleNode.addChild(libraryTestDependencyNode)

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
        (moduleDescription.targets, createModuleNode(moduleDescription, projectNode))
      } ++ rootModule.toSeq.map((Seq.empty, _))

      val idToModule = (for {
        (targets,module) <- modules
        target <- targets
      } yield {
        (target.id.get.uri.toURI, module)
      }).toMap

      createModuleDependencies(moduleDescriptions, idToModule)

      modules.foreach(m => projectNode.addChild(m._2))

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

  private case class ModuleDescription(targets: Seq[BuildTarget],
                                       targetDependencies: Seq[BuildTargetIdentifier],
                                       targetTestDependencies: Seq[BuildTargetIdentifier],
                                       basePath: File,
                                       output: Option[File],
                                       testOutput: Option[File],
                                       sourceDirs: Seq[File],
                                       testSourceDirs: Seq[File],
                                       classPath: Seq[File],
                                       testClassPath: Seq[File])

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

  private def calculateModuleDescription(targets: Seq[BuildTarget], optionsItems: Seq[ScalacOptionsItem], sourcesItems: Seq[DependencySourcesItem])
  : Iterable[ModuleDescription] = {
    val idToTarget = targets.map(t => (t.id.get, t)).toMap
    val idToScalaOptions = optionsItems.map(item => (item.target.get, item)).toMap
    val idToDepSources = sourcesItems.map(item => (item.target.get, item)).toMap

    def transitiveDependencyOutputs(start: BuildTarget) = {
      val transitiveDeps = (start +: transitiveDependencies(start)).map(_.id.get)
      transitiveDeps.map(idToScalaOptions).map(_.classDirectory.toFileAsURI)
    }

    def transitiveDependencies(start: BuildTarget): Seq[BuildTarget] = {
      val direct = start.dependencies.map(idToTarget)
      val transitive = direct.flatMap(transitiveDependencies)
      (start +: (direct ++ transitive)).distinct
    }

    val moduleDescriptions = targets.map { target =>
      val id = target.id.get
      val scalacOptions = idToScalaOptions(id)
      val sources = idToDepSources(id)

      // TODO use this to set scala sdk
      //  val scalaBuildTarget = JsonFormat.fromJsonString[ScalaBuildTarget](target.data.toStringUtf8)

      val sourceDirs = (for {
        src <- sources.uri
        file = src.toFileAsURI
        if !file.isFile // TODO ignores individual source files, will not work for every build tool
      } yield file).distinct

      val moduleBase = commonBase(sourceDirs).get.getCanonicalFile
      val outputPath = scalacOptions.classDirectory.toFileAsURI

      // classpath needs to be filtered for module dependency putput paths since they are handled by IDEA module dep mechanism
      val classPath = scalacOptions.classpath.map(_.toFileAsURI)
      val dependencyOutputs = transitiveDependencyOutputs(target)
      val classPathWithoutDependencyOutputs = classPath.filterNot(dependencyOutputs.contains)

      // TODO this is bloop-specific test scope detection. need something more general.
      if (id.uri.endsWith("-test"))
        ModuleDescription(Seq(target), Seq.empty, target.dependencies, moduleBase, None, Some(outputPath), Seq.empty, sourceDirs, Seq.empty, classPathWithoutDependencyOutputs)
      else
        ModuleDescription(Seq(target), target.dependencies, Seq.empty, moduleBase, Some(outputPath), None, sourceDirs, Seq.empty, classPathWithoutDependencyOutputs, Seq.empty)
    }

    // merge modules with the same calculated module base
    moduleDescriptions.groupBy(_.basePath).values.map(mergeModules)
  }


  private def createModuleDependencies(moduleDescriptions: Iterable[ModuleDescription], idToModule: Map[URI, DataNode[ModuleData]]) = {
    for {
      moduleDescription <- moduleDescriptions
      id <- moduleDescription.targets.head.id // any id will resolve the module in idToModule
      module <- idToModule.get(id.uri.toURI)
    } yield {
      val compileDeps = moduleDescription.targetDependencies.map((_, DependencyScope.COMPILE))
      val testDeps = moduleDescription.targetTestDependencies.map((_, DependencyScope.TEST))

      for {
        (moduleDepId, scope) <- compileDeps ++ testDeps
        moduleDep <- idToModule.get(moduleDepId.uri.toURI)
      } yield {
        val data = new ModuleDependencyData(module.getData, moduleDep.getData)
        data.setScope(scope)
        data.setExported(true)

        val node = new DataNode[ModuleDependencyData](ProjectKeys.MODULE_DEPENDENCY, data, module)
        module.addChild(node)
        (module)
      }
    }
  }


  /** Merge modules assuming they have the same base path. */
  private def mergeModules(descriptions: Seq[ModuleDescription]): ModuleDescription = {
    descriptions.reduce { (combined, next) =>
      // TODO ok it's time for monoids
      val targets = (combined.targets ++ next.targets).sortBy(_.id.get.uri)
      val targetDependencies = combined.targetDependencies ++ next.targetDependencies
      val targetTestDependencies = combined.targetTestDependencies ++ next.targetTestDependencies
      val output = combined.output.orElse(next.output)
      val testOutput = combined.testOutput.orElse(next.testOutput)
      val sourceDirs = combined.sourceDirs ++ next.sourceDirs
      val testSourceDirs  = combined.testSourceDirs ++ next.testSourceDirs
      val classPath = combined.classPath ++ next.classPath
      val testClassPath = combined.testClassPath ++ next.testClassPath

      ModuleDescription(targets, targetDependencies, targetTestDependencies, combined.basePath,
        output, testOutput, sourceDirs, testSourceDirs, classPath, testClassPath)
    }
  }

}
