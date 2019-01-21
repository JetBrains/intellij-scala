package org.jetbrains.bsp.project

import java.io.File
import java.net.URI
import java.util.Collections
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j._
import com.google.gson.{Gson, JsonElement}
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.{BspMetadata, SbtBuildModuleDataBsp, ScalaSdkData}
import org.jetbrains.bsp.project.BspProjectResolver._
import org.jetbrains.bsp.protocol.BspSession.{BspServer, NotificationCallback, ProcessLogger}
import org.jetbrains.bsp.protocol.{BspCommunication, BspJob, BspNotifications}
import org.jetbrains.bsp.settings.BspExecutionSettings
import org.jetbrains.bsp.{BSP, BspError, BspTaskCancelled}
import org.jetbrains.plugins.scala.project.Version

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, TimeoutException}

class BspProjectResolver extends ExternalSystemProjectResolver[BspExecutionSettings] {

  private var importState: ImportState = Inactive

  override def resolveProjectInfo(id: ExternalSystemTaskId,
                                  projectRootPath: String,
                                  isPreviewMode: Boolean,
                                  executionSettings: BspExecutionSettings,
                                  listener: ExternalSystemTaskNotificationListener): DataNode[ProjectData] = {

    val projectRoot = new File(projectRootPath)
    val moduleFilesDirectoryPath = new File(projectRootPath, ".idea/modules").getAbsolutePath

    def statusUpdate(msg: String): Unit = {
      val ev = new ExternalSystemTaskNotificationEvent(id, msg)
      listener.onStatusChange(ev)
    }

    def targetData(targetIds: List[BuildTargetIdentifier])(implicit bsp: BspServer):
    CompletableFuture[TargetData] =
      if (isPreviewMode) {
        val emptySources = Right[BspError,SourcesResult](new SourcesResult(Collections.emptyList()))
        val emptyDS = Right[BspError,DependencySourcesResult](new DependencySourcesResult(Collections.emptyList()))
        val emptySO = Right[BspError,ScalacOptionsResult](new ScalacOptionsResult(Collections.emptyList()))
        CompletableFuture.completedFuture(TargetData(emptySources, emptyDS, emptySO))
      } else {
        val targets = targetIds.asJava
        val sourcesParams = new SourcesParams(targets)
        val sources = bsp.buildTargetSources(sourcesParams).catchBspErrors
        val depSourcesParams = new DependencySourcesParams(targets)
        val depSources = bsp.buildTargetDependencySources(depSourcesParams).catchBspErrors
        val scalacOptionsParams = new ScalacOptionsParams(targets)
        val scalacOptions = bsp.buildTargetScalacOptions(scalacOptionsParams).catchBspErrors

        sources
          .thenCompose { src =>
            depSources.thenCompose { ds =>
              scalacOptions.thenApply { so =>
                TargetData(src,ds,so)
              }
            }
          }
      }

    def requests(implicit server: BspServer): CompletableFuture[Either[BspError, DataNode[ProjectData]]] = {
      val targetsRequest = server.workspaceBuildTargets()

      val projectNodeFuture: CompletableFuture[Either[BspError,DataNode[ProjectData]]] =
        targetsRequest.thenCompose { targetsResponse =>
          val targets = targetsResponse.getTargets.asScala
          val targetIds = targets.map(_.getId).toList
          val td = targetData(targetIds)
          td.thenApply { data =>
            for {
              sources <- data.sources
              depSources <- data.dependencySources // TODO not required for project, should be warning
              scalacOptions <- data.scalacOptions // TODO not required for non-scala modules
            } yield {
              val descriptions = calculateModuleDescriptions(
                targets,
                scalacOptions.getItems.asScala,
                sources.getItems.asScala,
                depSources.getItems.asScala
              )
              projectNode(descriptions)
            }
          }
        }

      projectNodeFuture
    }

    def createModuleNode(moduleDescription: ModuleDescription,
                              projectNode: DataNode[ProjectData]) = {

      import ExternalSystemSourceType._

      val moduleDescriptionData = moduleDescription.data

      val basePath = moduleDescriptionData.basePath.getCanonicalPath
      val contentRootData = new ContentRootData(BSP.ProjectSystemId, basePath)
      moduleDescriptionData.sourceDirs.foreach { dir =>
        val sourceType = if (dir.generated) SOURCE_GENERATED else SOURCE
        contentRootData.storePath(sourceType, dir.directory.getCanonicalPath)
      }
      moduleDescriptionData.testSourceDirs.foreach { dir =>
        val sourceType = if (dir.generated) TEST_GENERATED else TEST
        contentRootData.storePath(sourceType, dir.directory.getCanonicalPath)
      }

      val primaryTarget = moduleDescriptionData.targets.head
      val moduleId = primaryTarget.getId.getUri
      val moduleName = primaryTarget.getDisplayName
      val moduleData = new ModuleData(moduleId, BSP.ProjectSystemId, StdModuleTypes.JAVA.getId, moduleName, moduleFilesDirectoryPath, projectRootPath)

      moduleDescriptionData.output.foreach { outputPath =>
        moduleData.setCompileOutputPath(SOURCE, outputPath.getCanonicalPath)
      }
      moduleDescriptionData.testOutput.foreach { outputPath =>
        moduleData.setCompileOutputPath(TEST, outputPath.getCanonicalPath)
      }

      moduleData.setInheritProjectCompileOutputPath(false)

      val libraryData = new LibraryData(BSP.ProjectSystemId, s"$moduleName dependencies")
      moduleDescriptionData.classPath.foreach { path =>
        libraryData.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      moduleDescriptionData.classPathSources.foreach { path =>
        libraryData.addPath(LibraryPathType.SOURCE, path.getCanonicalPath)
      }
      val libraryDependencyData = new LibraryDependencyData(moduleData, libraryData, LibraryLevel.MODULE)
      libraryDependencyData.setScope(DependencyScope.COMPILE)

      val libraryTestData = new LibraryData(BSP.ProjectSystemId, s"$moduleName test dependencies")
      moduleDescriptionData.testClassPath.foreach { path =>
        libraryTestData.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
      }
      moduleDescriptionData.testClassPathSources.foreach { path =>
        libraryTestData.addPath(LibraryPathType.SOURCE, path.getCanonicalPath)
      }
      val libraryTestDependencyData = new LibraryDependencyData(moduleData, libraryTestData, LibraryLevel.MODULE)
      libraryTestDependencyData.setScope(DependencyScope.TEST)

      val targetIds = moduleDescriptionData.targets.map(_.getId.getUri.toURI)
      val metadata = BspMetadata(targetIds.asJava)

      // data node wiring

      val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)


      val libraryDependencyNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData, moduleNode)
      moduleNode.addChild(libraryDependencyNode)
      val libraryTestDependencyNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryTestDependencyData, moduleNode)
      moduleNode.addChild(libraryTestDependencyNode)

      val contentRootDataNode = new DataNode[ContentRootData](ProjectKeys.CONTENT_ROOT, contentRootData, moduleNode)
      moduleNode.addChild(contentRootDataNode)

      val metadataNode = new DataNode[BspMetadata](BspMetadata.Key, metadata, moduleNode)
      moduleNode.addChild(metadataNode)

      addNodeKindData(moduleNode, moduleDescription.moduleKindData)

      moduleNode
    }

    def addNodeKindData(moduleNode: DataNode[ModuleData], moduleKind: ModuleKind): Unit = {
      moduleKind match {
        case ScalaModule(scalaSdkData) =>

          val moduleData = moduleNode.getData

          val scalaSdkLibrary = new LibraryData(BSP.ProjectSystemId, ScalaSdkData.LibraryName)
          scalaSdkData.scalacClasspath.forEach { path =>
            scalaSdkLibrary.addPath(LibraryPathType.BINARY, path.getCanonicalPath)
          }
          val scalaSdkLibraryDependencyData = new LibraryDependencyData(moduleData, scalaSdkLibrary, LibraryLevel.MODULE)
          scalaSdkLibraryDependencyData.setScope(DependencyScope.COMPILE)

          val scalaSdkLibraryNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, scalaSdkLibraryDependencyData, moduleNode)
          moduleNode.addChild(scalaSdkLibraryNode)

          val scalaSdkNode = new DataNode[ScalaSdkData](ScalaSdkData.Key, scalaSdkData, moduleNode)
          moduleNode.addChild(scalaSdkNode)


        case SbtModule(scalaSdkData, sbtData) =>
          val scalaSdkNode = new DataNode[ScalaSdkData](ScalaSdkData.Key, scalaSdkData, moduleNode)
          val sbtNode = new DataNode[SbtBuildModuleDataBsp](SbtBuildModuleDataBsp.Key, sbtData, moduleNode)
          moduleNode.addChild(scalaSdkNode)
          moduleNode.addChild(sbtNode)
      }
    }

    def projectNode(moduleDescriptions: Iterable[ModuleDescription]): DataNode[ProjectData] = {

      statusUpdate("targets fetched") // TODO remove in favor of build toolwindow nodes

      val projectData = new ProjectData(BSP.ProjectSystemId, projectRoot.getName, projectRootPath, projectRootPath)
      val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)

      // synthetic root module when no natural module is at root
      val rootModule =
        if (moduleDescriptions.exists (_.data.basePath == projectRoot)) None
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
        (moduleDescription.data.targets, createModuleNode(moduleDescription, projectNode))
      } ++ rootModule.toSeq.map((Seq.empty, _))

      val idToModule = (for {
        (targets,module) <- modules
        target <- targets
      } yield {
        (target.getId.getUri, module)
      }).toMap

      createModuleDependencies(moduleDescriptions, idToModule)

      modules.foreach(m => projectNode.addChild(m._2))

      projectNode
    }

    val communication = BspCommunication.forBaseDir(projectRootPath, executionSettings)

    val notifications: NotificationCallback = {
      case BspNotifications.LogMessage(params) =>
        // TODO use params.id for tree structure
        statusUpdate(params.getMessage)
      case _ =>
    }

    val processLogger: ProcessLogger = { msg =>
      listener.onTaskOutput(id, msg, true)
    }

    val projectJob =
      communication.run(requests(_), notifications, processLogger)

    statusUpdate("starting task") // TODO remove in favor of build toolwindow nodes

    importState = Active(communication)
    val result = waitForProjectCancelable(projectJob)
    communication.closeSession()
    importState = Inactive

    statusUpdate("finished task") // TODO remove in favor of build toolwindow nodes

    result match {
      case Left(BspTaskCancelled) => null
      case Left(err) => throw err
      case Right(data) => data
    }
  }

  @tailrec private def waitForProjectCancelable[T](projectJob: BspJob[Either[BspError, DataNode[ProjectData]]]): Either[BspError, DataNode[ProjectData]] =
  importState match {
    case Active(_) =>
      try { Await.result(projectJob.future, 300.millis) }
      catch {
        case _: TimeoutException => waitForProjectCancelable(projectJob)
      }
    case Inactive =>
      projectJob.cancel()
      Left(BspTaskCancelled)
  }

  override def cancelTask(taskId: ExternalSystemTaskId,
                          listener: ExternalSystemTaskNotificationListener): Boolean =
    importState match {
      case Active(session) =>
        listener.beforeCancel(taskId)
        importState = Inactive
        session.closeSession()
        listener.onCancel(taskId)
        true
      case Inactive =>
        false
    }

}

object BspProjectResolver {

  private case class ModuleDescription(data: ModuleDescriptionData,
                                       moduleKindData: ModuleKind)

  private case class ModuleDescriptionData(targets: Seq[BuildTarget],
                                           targetDependencies: Seq[BuildTargetIdentifier],
                                           targetTestDependencies: Seq[BuildTargetIdentifier],
                                           basePath: File,
                                           output: Option[File],
                                           testOutput: Option[File],
                                           sourceDirs: Seq[SourceDirectory],
                                           testSourceDirs: Seq[SourceDirectory],
                                           classPath: Seq[File],
                                           classPathSources: Seq[File],
                                           testClassPath: Seq[File],
                                           testClassPathSources: Seq[File])

  sealed trait ModuleKind

  private case class ScalaModule(scalaSdkData: ScalaSdkData) extends ModuleKind

  private case class SbtModule(scalaSdkData: ScalaSdkData,
                               sbtData: SbtBuildModuleDataBsp
                              ) extends ModuleKind

  private case class TargetData(sources: Either[BspError, SourcesResult],
                                dependencySources: Either[BspError, DependencySourcesResult],
                                scalacOptions: Either[BspError, ScalacOptionsResult] // TODO should be optional
                               )

  private case class SourceDirectory(directory: File, generated: Boolean)

  private sealed abstract class ImportState
  private case class Active(communication: BspCommunication) extends ImportState
  private case object Inactive extends ImportState

  private val gson = new Gson()


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

  private def extractScalaSdkData(data: JsonElement): Option[ScalaBuildTarget] =
    Option(gson.fromJson[ScalaBuildTarget](data, classOf[ScalaBuildTarget]))

  private def extractSbtData(data: JsonElement): Option[SbtBuildTarget] =
    Option(gson.fromJson[SbtBuildTarget](data, classOf[SbtBuildTarget]))

  private def calculateScalaSdkData(target: ScalaBuildTarget): ScalaSdkData = {
    ScalaSdkData(
      target.getScalaOrganization,
      Some(Version(target.getScalaVersion)),
      scalacClasspath = target.getJars.asScala.map(_.toURI.toFile).asJava,
      Collections.emptyList(),
      None,
      Collections.emptyList()
    )
  }

  // TODO create SbtModuleDescription from data
  private def calculateSbtData(sbtBuildTarget: SbtBuildTarget): (SbtBuildModuleDataBsp, ScalaSdkData) = {
    val buildFor = sbtBuildTarget.getChildren.asScala.map { target => new URI(target.getUri) }

    val sbtBuildModuleData = SbtBuildModuleDataBsp(
      sbtBuildTarget.getAutoImports,
      buildFor.asJava
    )
    val scalaSdkData = calculateScalaSdkData(sbtBuildTarget.getScalaBuildTarget)

    (sbtBuildModuleData, scalaSdkData)
  }

  private def calculateModuleDescriptions(
    buildTargets: Seq[BuildTarget],
    optionsItems: Seq[ScalacOptionsItem],
    sourcesItems: Seq[SourcesItem],
    dependencySourcesItems: Seq[DependencySourcesItem]
  ): Iterable[ModuleDescription] = {

    val idToTarget = buildTargets.map(t => (t.getId, t)).toMap
    val idToScalaOptions = optionsItems.map(item => (item.getTarget, item)).toMap
    val idToDepSources = dependencySourcesItems.map(item => (item.getTarget, item)).toMap
    val idToSources = sourcesItems.map(item => (item.getTarget, item)).toMap

    def transitiveDependencyOutputs(start: BuildTarget): Seq[File] = {
      val transitiveDeps = (start +: transitiveDependencies(start)).map(_.getId)
      transitiveDeps.flatMap(idToScalaOptions.get).map(_.getClassDirectory.toURI.toFile)
    }

    def transitiveDependencies(start: BuildTarget): Seq[BuildTarget] = {
      val direct = start.getDependencies.asScala.map(idToTarget)
      val transitive = direct.flatMap(transitiveDependencies)
      (start +: (direct ++ transitive)).distinct
    }

    val moduleDescriptions = buildTargets.flatMap { target: BuildTarget =>
      val id = target.getId
      val scalacOptions = idToScalaOptions.get(id)
      val depSourcesOpt = idToDepSources.get(id)
      val sourcesOpt = idToSources.get(id)

      val sourceItems = (for {
        sources <- sourcesOpt.toSeq
        src <- sources.getSources.asScala
      } yield src).distinct

      // FIXME use me
      val depSourcePaths = for {
        depSources <- depSourcesOpt.toSeq
        depSrc <- depSources.getSources.asScala
      } yield depSrc.toURI.toFile

      // TODO bsp spec depends on uri ending in `/` to determine directory
      // https://github.com/scalacenter/bsp/issues/76
      val sourceDirs = sourceItems
        .map { item =>
          val file = item.getUri.toURI.toFile
          if (item.getUri.endsWith("/"))
            SourceDirectory(file, item.getGenerated)
          else
            // just use the file's immediate parent as best guess of source dir
            // IntelliJ project model doesn't have a concept of individual source files
            SourceDirectory(file.getParentFile, item.getGenerated)
          }
        .distinct

      // all subdirectories of a source dir are automatically source dirs
      val sourceRoots = sourceDirs.filter {dir =>
        ! sourceDirs.exists(a => FileUtil.isAncestor(a.directory, dir.directory, true))
      }

      val dependencySources = dependencySourcesItems.flatMap(_.getSources.asScala).map(_.toURI.toFile)

      val moduleBase = target.getId.getUri.toURI.toFile
      val outputPath = scalacOptions.map(_.getClassDirectory.toURI.toFile)

      // classpath needs to be filtered for module dependency output paths since they are handled by IDEA module dep mechanism
      val classPath = scalacOptions.map(_.getClasspath.asScala.map(_.toURI.toFile))
      val dependencyOutputs = transitiveDependencyOutputs(target)
      val classPathWithoutDependencyOutputs = classPath.getOrElse(Seq.empty).filterNot(dependencyOutputs.contains)

      val tags = target.getTags.asScala

      val targetData =
        if (tags.contains(BuildTargetTag.NO_IDE)) None
        else Option(target.getData).map(_.asInstanceOf[JsonElement])

      val moduleDescriptionData = createScalaModuleDescription(
        target, tags, moduleBase, outputPath, sourceRoots,
        classPathWithoutDependencyOutputs, dependencySources)

      val scalaModule =
        targetData.flatMap(extractScalaSdkData)
          .map(calculateScalaSdkData)
          .map(ScalaModule)

      val sbtModule =
        targetData.flatMap(extractSbtData)
          .map { data =>
            val (sbtModuleData, scalaSdkData) = calculateSbtData(data)
            SbtModule(scalaSdkData, sbtModuleData)
          }

      scalaModule.orElse(sbtModule)
        .map(moduleKind => ModuleDescription(moduleDescriptionData, moduleKind))
        .toSeq
    }

    // merge modules with the same module base
    moduleDescriptions.groupBy(_.data.basePath).values.map(mergeModules)

    moduleDescriptions
  }

  private def createScalaModuleDescription(target: BuildTarget,
                                           tags: Seq[String],
                                           moduleBase: File,
                                           outputPath: Option[File],
                                           sourceRoots: Seq[SourceDirectory],
                                           classPath: Seq[File],
                                           dependencySources: Seq[File]
                                          ) = {
    import BuildTargetTag._

    if (tags.contains(LIBRARY) || tags.contains(APPLICATION))
      ModuleDescriptionData(
        targets = Seq(target),
        targetDependencies = target.getDependencies.asScala,
        targetTestDependencies = Seq.empty,
        basePath = moduleBase,
        output = outputPath,
        testOutput = None,
        sourceDirs = sourceRoots,
        testSourceDirs = Seq.empty,
        classPath = classPath,
        classPathSources = dependencySources,
        testClassPath = Seq.empty,
        testClassPathSources = Seq.empty
      )
    else if(tags.contains(TEST))
      ModuleDescriptionData(
        targets = Seq(target),
        targetDependencies = Seq.empty,
        targetTestDependencies = target.getDependencies.asScala,
        basePath = moduleBase,
        output = None,
        testOutput = outputPath,
        sourceDirs = Seq.empty,
        testSourceDirs = sourceRoots,
        classPath = Seq.empty,
        classPathSources = Seq.empty,
        testClassPath = classPath,
        testClassPathSources = dependencySources
      )
    else // create a module, but with empty classpath
      ModuleDescriptionData(
        Seq(target),
        Seq.empty, Seq.empty,
        moduleBase,
        None, None,
        Seq.empty, Seq.empty,
        Seq.empty, Seq.empty,
        Seq.empty, Seq.empty
      ) // TODO ignore and warn about unsupported build target kinds? map to special module?
  }



  private def createModuleDependencies(moduleDescriptions: Iterable[ModuleDescription], idToModule: Map[String, DataNode[ModuleData]]) = {
    for {
      moduleDescription <- moduleDescriptions
      id = moduleDescription.data.targets.head.getId.getUri // any id will resolve the module in idToModule
      module <- idToModule.get(id)
    } yield {
      val compileDeps = moduleDescription.data.targetDependencies.map((_, DependencyScope.COMPILE))
      val testDeps = moduleDescription.data.targetTestDependencies.map((_, DependencyScope.TEST))

      val moduleDeps = for {
        (moduleDepId, scope) <- compileDeps ++ testDeps
        moduleDep <- idToModule.get(moduleDepId.getUri)
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
  private def mergeModules(descriptions: Seq[ModuleDescription]): ModuleDescription = {
    descriptions.reduce { (combined, next) =>
      val dataCombined = combined.data
      val dataNext = next.data
      // TODO ok it's time for monoids
      val targets = (dataCombined.targets ++ dataNext.targets).sortBy(_.getId.getUri)
      val targetDependencies = dataCombined.targetDependencies ++ dataNext.targetDependencies
      val targetTestDependencies = dataCombined.targetTestDependencies ++ dataNext.targetTestDependencies
      val output = dataCombined.output.orElse(dataNext.output)
      val testOutput = dataCombined.testOutput.orElse(dataNext.testOutput)
      val sourceDirs = dataCombined.sourceDirs ++ dataNext.sourceDirs
      val testSourceDirs  = dataCombined.testSourceDirs ++ dataNext.testSourceDirs
      val classPath = dataCombined.classPath ++ dataNext.classPath
      val classPathSources = dataCombined.classPathSources ++ dataNext.classPathSources
      val testClassPath = dataCombined.testClassPath ++ dataNext.testClassPath
      val testClassPathSources = dataCombined.testClassPathSources ++ dataNext.testClassPathSources

      val newData = ModuleDescriptionData(
        targets, targetDependencies, targetTestDependencies, dataCombined.basePath,
        output, testOutput, sourceDirs, testSourceDirs,
        classPath, classPathSources, testClassPath, testClassPathSources,
      )

      combined.copy(data = newData)
    }
  }

}
