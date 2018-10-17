package org.jetbrains.bsp.project

import java.io.File

import cats.data.EitherT
import ch.epfl.scala.bsp._
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import io.circe.Json
import monix.eval.Task
import monix.execution.{ExecutionModel, Scheduler}
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.{BspMetadata, ScalaSdkData}
import org.jetbrains.bsp.magic.monixToCats.monixToCatsMonad
import org.jetbrains.bsp.project.BspProjectResolver._
import org.jetbrains.bsp.protocol.BspCommunication
import org.jetbrains.bsp.protocol.BspCommunication.NotificationCallback
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.bsp.{BSP, BspError, BspErrorMessage}
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.data.SbtBuildModuleData

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration._
import scala.meta.jsonrpc.{LanguageClient, Response}

class BspProjectResolver extends ExternalSystemProjectResolver[BspExecutionSettings] {

  private var importState: ImportState = Inactive

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectRootPath: String,
                                  isPreviewMode: Boolean,
                                  executionSettings: BspExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {

    val projectRoot = new File(projectRootPath)
    val moduleFilesDirectoryPath = new File(projectRootPath, ".idea/modules").getAbsolutePath

    implicit val scheduler: Scheduler = Scheduler(PooledThreadExecutor.INSTANCE, ExecutionModel.AlwaysAsyncExecution)

    def statusUpdate(msg: String): Unit = {
      val ev = new ExternalSystemTaskNotificationEvent(id, msg)
      listener.onStatusChange(ev)
    }

    def targetData(targetIds: List[BuildTargetIdentifier])(implicit client: LanguageClient):
    Task[(Either[Response.Error, DependencySourcesResult],
          Either[Response.Error, ScalacOptionsResult])] =
      if (isPreviewMode)
        Task.now(Right(DependencySourcesResult(List.empty)), Right(ScalacOptionsResult(List.empty)))
      else {
        import endpoints.BuildTarget._
        val depSources = dependencySources.request(DependencySourcesParams(targetIds))
        val scalacOptions = endpoints.BuildTarget.scalacOptions.request(ScalacOptionsParams(targetIds))
        Task.zip2(depSources, scalacOptions)
      }

    def requests(implicit client: LanguageClient): Task[Either[BspError, Iterable[ScalaModuleDescription]]] = {
      import endpoints._
      val targetsRequest = Workspace.buildTargets.request(WorkspaceBuildTargetsRequest())
      val transformer = for {
        targetsResponse <- EitherT(targetsRequest).leftMap(_.toBspError)
        targets = targetsResponse.targets
        targetIds = targets.map(_.id)
        data <- EitherT.right(targetData(targetIds))
        dependencySources <- EitherT.fromEither[Task](data._1.left.map(_.toBspError)) // TODO not required for project, should be warning
        scalacOptions <- EitherT.fromEither[Task](data._2.left.map(_.toBspError)) // TODO not required for non-scala modules
      } yield {
        calculateModuleDescription(targets, scalacOptions.items, dependencySources.items)
      }
      transformer.value
    }

    def createModuleNode(moduleDescription: ScalaModuleDescription,
                         projectNode: DataNode[ProjectData]) = {

      val basePath = moduleDescription.basePath.getCanonicalPath
      val contentRootData = new ContentRootData(BSP.ProjectSystemId, basePath)
      moduleDescription.sourceDirs.foreach { dir =>
        contentRootData.storePath(ExternalSystemSourceType.SOURCE, dir.getCanonicalPath)
      }
      moduleDescription.testSourceDirs.foreach { dir =>
        contentRootData.storePath(ExternalSystemSourceType.TEST, dir.getCanonicalPath)
      }

      val primaryTarget = moduleDescription.targets.head
      val moduleId = primaryTarget.id.uri.toString
      val moduleName = primaryTarget.displayName
      val moduleData = new ModuleData(moduleId, BSP.ProjectSystemId, StdModuleTypes.JAVA.getId, moduleName, moduleFilesDirectoryPath, projectRootPath)

      moduleDescription.output.foreach { outputPath =>
        moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, outputPath.getCanonicalPath)
      }
      moduleDescription.testOutput.foreach { outputPath =>
        moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, outputPath.getCanonicalPath)
      }

      moduleData.setInheritProjectCompileOutputPath(false)

      val scalaSdkLibrary = new LibraryData(BSP.ProjectSystemId, ScalaSdkData.LibraryName)
      moduleDescription.scalaSdkData.scalacClasspath.foreach { path =>
        scalaSdkLibrary.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      val scalaSdkLibraryDependencyData = new LibraryDependencyData(moduleData, scalaSdkLibrary, LibraryLevel.MODULE)
      scalaSdkLibraryDependencyData.setScope(DependencyScope.COMPILE)

      val libraryData = new LibraryData(BSP.ProjectSystemId, s"$moduleName dependencies")
      moduleDescription.classPath.foreach { path =>
        libraryData.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      moduleDescription.classPathSources.foreach { path =>
        libraryData.addPath(LibraryPathType.SOURCE, path.getCanonicalPath)
      }
      val libraryDependencyData = new LibraryDependencyData(moduleData, libraryData, LibraryLevel.MODULE)
      libraryDependencyData.setScope(DependencyScope.COMPILE)

      val libraryTestData = new LibraryData(BSP.ProjectSystemId, s"$moduleName test dependencies")
      moduleDescription.testClassPath.foreach { path =>
        libraryTestData.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      moduleDescription.testClassPathSources.foreach { path =>
        libraryTestData.addPath(LibraryPathType.SOURCE, path.getCanonicalPath)
      }
      val libraryTestDependencyData = new LibraryDependencyData(moduleData, libraryTestData, LibraryLevel.MODULE)
      libraryTestDependencyData.setScope(DependencyScope.TEST)

      val targetIds = moduleDescription.targets.map(_.id)
      val metadata = BspMetadata(targetIds.toList)

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

    def projectNode(moduleDescriptions: Iterable[ScalaModuleDescription]) = {

      statusUpdate("targets fetched") // TODO remove in favor of build toolwindow nodes

      val projectData = new ProjectData(BSP.ProjectSystemId, projectRoot.getName, projectRootPath, projectRootPath)
      val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)

      // synthetic root module when no natural module is at root
      val rootModule =
        if (moduleDescriptions.exists (_.basePath == projectRoot)) None
        else {
          val name = projectRoot.getName + "-root"
          val moduleData = new ModuleData(name, BSP.ProjectSystemId, BspSyntheticModuleType.Id, name, moduleFilesDirectoryPath, projectRootPath)
          val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)
          val contentRootData = new ContentRootData(BSP.ProjectSystemId, projectRoot.getCanonicalPath)
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

    val logger = Logger.getInstance(classOf[BspProjectResolver])

    val communication = BspCommunication.forBaseDir(projectRootPath, executionSettings)

    val bspNotifications: NotificationCallback = {
      case BspCommunication.LogMessage(params) =>
        // TODO use params.id for tree structure
        statusUpdate(params.message)
      case _ =>
    }

    val projectFuture =
      communication.run(requests(_), bspNotifications )
        .map{ moduleDescriptionsResult =>
          moduleDescriptionsResult.map(projectNode)
        }

    statusUpdate("starting task") // TODO remove in favor of build toolwindow nodes

    importState = Active(communication)
    val result = busyAwaitProject(projectFuture)
    importState = Inactive

    statusUpdate("finished task") // TODO remove in favor of build toolwindow nodes

    result match {
      case Left(err) => throw err
      case Right(data) => data
    }
  }

  @tailrec private def busyAwaitProject(projectFuture: Future[Either[BspError, DataNode[ProjectData]]]): Either[BspError, DataNode[ProjectData]] =
  importState match {
    case Active(_) =>
      try {Await.result(projectFuture, 300.millis)}
      catch {
        case _: TimeoutException => busyAwaitProject(projectFuture)
      }
    case Inactive =>
      Left(BspErrorMessage("Import canceled"))
  }

  override def cancelTask(taskId: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener): Boolean =
    importState match {
      case Active(session) =>
        listener.beforeCancel(taskId)
        importState = Inactive
        Await.ready(session.closeSession(), 10.seconds)
        listener.onCancel(taskId)
        true
      case Inactive =>
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
                                            classPathSources: Seq[File],
                                            testClassPath: Seq[File],
                                            testClassPathSources: Seq[File],
                                            scalaSdkData: ScalaSdkData
                                           ) extends ModuleDescription

  private case class SbtModuleDescription(sbtData: SbtBuildModuleData,
                                          scalaModule: ScalaModuleDescription
                                         ) extends ModuleDescription

  private sealed abstract class ImportState
  private case class Active(communication: BspCommunication) extends ImportState
  private case object Inactive extends ImportState


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

      val sourcePaths = (for {
        sources <- sourcesOpt.toSeq
        src <- sources.uris
      } yield src.toFile).distinct

      // TODO dependencySources gives us both project source dirs as well as actual dependency sources currently.
      // needs to be changed in bsp to allow determining which path is which robustly
      val sourceDirs = sourcePaths.filter(_.isDirectory)
      // hacky, but works for now
      val dependencySources = sourcePaths.filter(_.getName.endsWith("jar"))

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
            targets = Seq(target),
            targetDependencies = target.dependencies,
            targetTestDependencies = Seq.empty,
            basePath = moduleBase,
            output = outputPath,
            testOutput = None,
            sourceDirs = sourceDirs,
            testSourceDirs = Seq.empty,
            classPath = classPathWithoutDependencyOutputs,
            classPathSources = dependencySources,
            testClassPath = Seq.empty,
            testClassPathSources = Seq.empty,
            scalaSdkData = scalaSdkData
          )
        else if(target.kind == BuildTargetKind.Test)
          ScalaModuleDescription(
            targets = Seq(target),
            targetDependencies = Seq.empty,
            targetTestDependencies = target.dependencies,
            basePath = moduleBase,
            output = None,
            testOutput = outputPath,
            sourceDirs = Seq.empty,
            testSourceDirs = sourceDirs,
            classPath = Seq.empty,
            classPathSources = Seq.empty,
            testClassPath = classPathWithoutDependencyOutputs,
            testClassPathSources = dependencySources,
            scalaSdkData = scalaSdkData
          )
        else
          ScalaModuleDescription(
            Seq(target),
            Seq.empty, Seq.empty,
            moduleBase,
            None, None,
            Seq.empty, Seq.empty,
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
      val classPathSources = combined.classPathSources ++ next.classPathSources
      val testClassPath = combined.testClassPath ++ next.testClassPath
      val testClassPathSources = combined.testClassPathSources ++ next.testClassPathSources
      // Get the ScalaSdkData from the first combined module
      val scalaSdkData = combined.scalaSdkData

      ScalaModuleDescription(
        targets, targetDependencies, targetTestDependencies, combined.basePath,
        output, testOutput, sourceDirs, testSourceDirs,
        classPath, classPathSources, testClassPath, testClassPathSources,
        scalaSdkData
      )
    }
  }

}
