package org.jetbrains.bsp

import java.io.File

import ch.epfl.scala.bsp._
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import io.circe.Json
import monix.eval.Task
import monix.execution.{Cancelable, ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspProjectResolver._
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.{BspMetadata, ScalaSdkData}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.data.SbtBuildModuleData

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.meta.jsonrpc.{Response, Services}
import scala.meta.lsp.LanguageClient

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

    def targetData(targetIds: List[BuildTargetIdentifier])(implicit client: LanguageClient):
    Task[(Either[Response.Error, DependencySourcesResult], Either[Response.Error, ScalacOptionsResult])] =
      if (isPreviewMode)
        Task.now(Right(DependencySourcesResult(List.empty)), Right(ScalacOptionsResult(List.empty)))
      else Task.zip2(
        endpoints.BuildTarget.dependencySources.request(DependencySourcesParams(targetIds)),
        endpoints.BuildTarget.scalacOptions.request(ScalacOptionsParams(targetIds))
      )

    def requests(implicit client: LanguageClient) = {
      import endpoints._
      for {
        targetsResponse <- Workspace.buildTargets.request(WorkspaceBuildTargetsRequest())
        targets = targetsResponse.right.get.targets // YOLO
        targetIds = targets.map(_.id)
        data <- targetData(targetIds)
      } yield {
        calculateModuleDescription(targets, data._2.right.get.items, data._1.right.get.items) // YOLO
      }
    }
    
    def createModuleNode(moduleDescription: ScalaModuleDescription,
                         projectNode: DataNode[ProjectData]) = {

      val basePath = moduleDescription.basePath.getCanonicalPath
      val contentRootData = new ContentRootData(bsp.ProjectSystemId, basePath)
      moduleDescription.sourceDirs.foreach { dir =>
        contentRootData.storePath(ExternalSystemSourceType.SOURCE, dir.getCanonicalPath)
      }
      moduleDescription.testSourceDirs.foreach { dir =>
        contentRootData.storePath(ExternalSystemSourceType.TEST, dir.getCanonicalPath)
      }

      val primaryTarget = moduleDescription.targets.head
      val moduleId = primaryTarget.id.uri.toString
      val moduleName = primaryTarget.displayName
      val moduleData = new ModuleData(moduleId, bsp.ProjectSystemId, StdModuleTypes.JAVA.getId, moduleName, moduleFilesDirectoryPath, projectRootPath)

      moduleDescription.output.foreach { outputPath =>
        moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, outputPath.getCanonicalPath)
      }
      moduleDescription.testOutput.foreach { outputPath =>
        moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, outputPath.getCanonicalPath)
      }

      moduleData.setInheritProjectCompileOutputPath(false)

      val scalaSdkLibrary = new LibraryData(bsp.ProjectSystemId, ScalaSdkData.LibraryName)
      moduleDescription.scalaSdkData.scalacClasspath.foreach { path =>
        scalaSdkLibrary.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      val scalaSdkLibraryDependencyData = new LibraryDependencyData(moduleData, scalaSdkLibrary, LibraryLevel.MODULE)
      scalaSdkLibraryDependencyData.setScope(DependencyScope.COMPILE)

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

      val targetIds = moduleDescription.targets.map(_.id)
      val metadata = BspMetadata(targetIds)

      // data node wiring
      // TODO refactor and reuse sbt module wiring api

      val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)

      val scalaSdkLibraryNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, scalaSdkLibraryDependencyData, moduleNode)
      moduleNode.addChild(scalaSdkLibraryNode)
      val libraryDependencyNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData, moduleNode)
      moduleNode.addChild(libraryDependencyNode)
      val libraryTestDependencyNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryTestDependencyData, moduleNode)
      moduleNode.addChild(libraryTestDependencyNode)

      val contentRootDataNode = new DataNode[ContentRootData](ProjectKeys.CONTENT_ROOT, contentRootData, moduleNode)
      moduleNode.addChild(contentRootDataNode)

      val scalaSdkNode = new DataNode[ScalaSdkData](ScalaSdkData.Key, moduleDescription.scalaSdkData, moduleNode)
      moduleNode.addChild(scalaSdkNode)

      val metadataNode = new DataNode[BspMetadata](BspMetadata.Key, metadata, moduleNode)
      moduleNode.addChild(metadataNode)

      moduleNode
    }

    val services = Services.empty
      .notification(endpoints.Build.logMessage) { params =>
        // TODO use params.id for tree structure
        statusUpdate(params.message)
      }

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
        (target.id.uri, module)
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

  override def cancelTask(taskId: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener): Boolean =
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

  sealed trait ModuleDescription

  private case class ScalaModuleDescription(targets: Seq[BuildTarget],
                                            targetDependencies: Seq[BuildTargetIdentifier],
                                            targetTestDependencies: Seq[BuildTargetIdentifier],
                                            basePath: File,
                                            output: Option[File],
                                            testOutput: Option[File],
                                            sourceDirs: Seq[File],
                                            testSourceDirs: Seq[File],
                                            classPath: Seq[File],
                                            testClassPath: Seq[File],
                                            scalaSdkData: ScalaSdkData
                                           ) extends ModuleDescription

  private case class SbtModuleDescription(sbtData: SbtBuildModuleData,
                                          scalaModule: ScalaModuleDescription
                                         ) extends ModuleDescription

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

  private def extractScalaSdkData(data: Json): Option[ScalaSdkData] = {
    val result = data.as[ScalaBuildTarget].map { target =>
      ScalaSdkData(
        target.scalaOrganization,
        Some(Version(target.scalaVersion)),
        scalacClasspath = target.jars.map(_.toFile),
        Nil,
        None,
        Nil
      )
    }

    // TODO any need to propagate failure?
    result.toOption
  }

  // TODO create SbtModuleDescription from data
  private def extractSbtData(data: Json): Option[SbtModuleDescription] = {
    data.as[ScalaBuildTarget]
    None
  }

  private def calculateModuleDescription(targets: Seq[BuildTarget], optionsItems: Seq[ScalacOptionsItem], sourcesItems: Seq[DependencySourcesItem])
  : Iterable[ScalaModuleDescription] = {
    val idToTarget = targets.map(t => (t.id, t)).toMap
    val idToScalaOptions = optionsItems.map(item => (item.target, item)).toMap
    val idToDepSources = sourcesItems.map(item => (item.target, item)).toMap

    def transitiveDependencyOutputs(start: BuildTarget) = {
      val transitiveDeps = (start +: transitiveDependencies(start)).map(_.id)
      transitiveDeps.flatMap(idToScalaOptions.get).map(_.classDirectory.toFile)
    }

    def transitiveDependencies(start: BuildTarget): Seq[BuildTarget] = {
      val direct = start.dependencies.map(idToTarget)
      val transitive = direct.flatMap(transitiveDependencies)
      (start +: (direct ++ transitive)).distinct
    }

    val moduleDescriptions = targets.flatMap { target: BuildTarget =>
      val id = target.id
      val scalacOptions = idToScalaOptions.get(id)
      val sourcesOpt = idToDepSources.get(id)

      val sourceDirs = (for {
        sources <- sourcesOpt.toSeq
        src <- sources.uris
        file = src.toFile
        if !file.isFile // TODO ignores individual source files, will not work for every build tool
      } yield file).distinct

      val targetUri = target.id.uri
      val moduleBase = targetUri.toFile
      val outputPath = scalacOptions.map(_.classDirectory.toFile)

      // classpath needs to be filtered for module dependency putput paths since they are handled by IDEA module dep mechanism
      val classPath = scalacOptions.map(_.classpath.map(_.toFile))
      val dependencyOutputs = transitiveDependencyOutputs(target)
      val classPathWithoutDependencyOutputs = classPath.getOrElse(Seq.empty).filterNot(dependencyOutputs.contains)

      val description = for {
        data <- target.data
        scalaSdkData <- extractScalaSdkData(data)
      } yield {

        if(target.kind == BuildTargetKind.Library || target.kind == BuildTargetKind.App)
          ScalaModuleDescription(
            Seq(target),
            target.dependencies, Seq.empty,
            moduleBase,
            outputPath, None,
            sourceDirs, Seq.empty,
            classPathWithoutDependencyOutputs, Seq.empty,
            scalaSdkData
          )
        else if(target.kind == BuildTargetKind.Test)
          ScalaModuleDescription(
            Seq(target),
            Seq.empty, target.dependencies,
            moduleBase,
            None, outputPath,
            Seq.empty, sourceDirs,
            Seq.empty, classPathWithoutDependencyOutputs,
            scalaSdkData)
        else
          ScalaModuleDescription(
            Seq(target),
            Seq.empty, Seq.empty,
            moduleBase,
            None, None,
            Seq.empty, Seq.empty,
            Seq.empty, Seq.empty,
            scalaSdkData
          ) // TODO ignore and warn about unsupported build target kinds? map to special module?
      }

      description.toSeq
    }

    // merge modules with the same module base
    moduleDescriptions.groupBy(_.basePath).values.map(mergeModules)
  }




  private def createModuleDependencies(moduleDescriptions: Iterable[ScalaModuleDescription], idToModule: Map[Uri, DataNode[ModuleData]]) = {
    for {
      moduleDescription <- moduleDescriptions
      id = moduleDescription.targets.head.id // any id will resolve the module in idToModule
      module <- idToModule.get(id.uri)
    } yield {
      val compileDeps = moduleDescription.targetDependencies.map((_, DependencyScope.COMPILE))
      val testDeps = moduleDescription.targetTestDependencies.map((_, DependencyScope.TEST))

      val moduleDeps = for {
        (moduleDepId, scope) <- compileDeps ++ testDeps
        moduleDep <- idToModule.get(moduleDepId.uri)
      } yield {
        val data = new ModuleDependencyData(module.getData, moduleDep.getData)
        data.setScope(scope)
        data.setExported(true)

        val node = new DataNode[ModuleDependencyData](ProjectKeys.MODULE_DEPENDENCY, data, module)
        module.addChild(node)
        moduleDep
      }
      (module, moduleDeps)
    }
  }


  /** Merge modules assuming they have the same base path. */
  private def mergeModules(descriptions: Seq[ScalaModuleDescription]): ScalaModuleDescription = {
    descriptions.reduce { (combined, next) =>
      // TODO ok it's time for monoids
      val targets = (combined.targets ++ next.targets).sortBy(_.id.uri.value)
      val targetDependencies = combined.targetDependencies ++ next.targetDependencies
      val targetTestDependencies = combined.targetTestDependencies ++ next.targetTestDependencies
      val output = combined.output.orElse(next.output)
      val testOutput = combined.testOutput.orElse(next.testOutput)
      val sourceDirs = combined.sourceDirs ++ next.sourceDirs
      val testSourceDirs  = combined.testSourceDirs ++ next.testSourceDirs
      val classPath = combined.classPath ++ next.classPath
      val testClassPath = combined.testClassPath ++ next.testClassPath
      // Get the ScalaSdkData from the first combined module
      val scalaSdkData = combined.scalaSdkData

      ScalaModuleDescription(
        targets, targetDependencies, targetTestDependencies, combined.basePath,
        output, testOutput, sourceDirs, testSourceDirs, classPath, testClassPath, scalaSdkData)
    }
  }

}
