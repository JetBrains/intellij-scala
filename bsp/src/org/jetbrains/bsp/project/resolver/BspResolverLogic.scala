package org.jetbrains.bsp.project.resolver

import java.io.File
import java.net.URI
import java.util.Collections

import ch.epfl.scala.bsp4j._
import com.google.gson.{Gson, JsonElement}
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data.{BspMetadata, SbtBuildModuleDataBsp, ScalaSdkData}
import org.jetbrains.bsp.project.BspSyntheticModuleType
import org.jetbrains.bsp.project.resolver.BspResolverDescriptors._
import org.jetbrains.plugins.scala.project.Version

import scala.collection.JavaConverters._

private[resolver] object BspResolverLogic {

  private def extractScalaSdkData(data: JsonElement)(implicit gson: Gson): Option[ScalaBuildTarget] =
    Option(gson.fromJson[ScalaBuildTarget](data, classOf[ScalaBuildTarget]))

  private def extractSbtData(data: JsonElement)(implicit gson: Gson): Option[SbtBuildTarget] =
    Option(gson.fromJson[SbtBuildTarget](data, classOf[SbtBuildTarget]))

  /** Find common base path of all given files */
  private[resolver] def commonBase(dirs: Seq[File]): Option[File] = {
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


  private[resolver] def getScalaSdkData(target: ScalaBuildTarget, scalacOptionsItem: Option[ScalacOptionsItem]): ScalaSdkData = {
    val scalaOptionsStrings = scalacOptionsItem.map(item => item.getOptions).getOrElse(Collections.emptyList())
    ScalaSdkData(
      scalaOrganization = target.getScalaOrganization,
      scalaVersion = Some(Version(target.getScalaVersion)),
      scalacClasspath = target.getJars.asScala.map(_.toURI.toFile).asJava,
      scalacOptions = scalaOptionsStrings
    )
  }

  private[resolver] def getSbtData(target: SbtBuildTarget, scalacOptionsItem: Option[ScalacOptionsItem]): (SbtBuildModuleDataBsp, ScalaSdkData) = {
    val buildFor = target.getChildren.asScala.map { target => new URI(target.getUri) }

    val sbtBuildModuleData = SbtBuildModuleDataBsp(
      target.getAutoImports,
      buildFor.asJava
    )
    val scalaSdkData = getScalaSdkData(target.getScalaBuildTarget, scalacOptionsItem)

    (sbtBuildModuleData, scalaSdkData)
  }

  private[resolver] def calculateModuleDescriptions(buildTargets: Seq[BuildTarget],
                                                    optionsItems: Seq[ScalacOptionsItem],
                                                    sourcesItems: Seq[SourcesItem],
                                                    dependencySourcesItems: Seq[DependencySourcesItem]
                                                   ): Iterable[ModuleDescription] = {

    implicit val gson: Gson = new Gson()

    val idToTarget = buildTargets.map(t => (t.getId, t)).toMap
    val idToScalacOptions = optionsItems.map(item => (item.getTarget, item)).toMap
    val idToDepSources = dependencySourcesItems.map(item => (item.getTarget, item)).toMap
    val idToSources = sourcesItems.map(item => (item.getTarget, item)).toMap

    def transitiveDependencyOutputs(start: BuildTarget): Seq[File] = {
      val transitiveDeps = (start +: transitiveDependencies(start)).map(_.getId)
      transitiveDeps.flatMap(idToScalacOptions.get).map(_.getClassDirectory.toURI.toFile)
    }

    def transitiveDependencies(start: BuildTarget): Seq[BuildTarget] = {
      val direct = start.getDependencies.asScala.map(idToTarget)
      val transitive = direct.flatMap(transitiveDependencies)
      (start +: (direct ++ transitive)).distinct
    }

    val moduleDescriptions = buildTargets.flatMap { target: BuildTarget =>
      val id = target.getId
      val scalacOptions = idToScalacOptions.get(id)
      val depSourcesOpt = idToDepSources.get(id)
      val sourcesOpt = idToSources.get(id)
      val dependencyOutputs = transitiveDependencyOutputs(target)

      moduleDescriptionsForTarget(target, scalacOptions, depSourcesOpt, sourcesOpt, dependencyOutputs)
    }

    // merge modules with the same module base
    moduleDescriptions.groupBy(_.data.basePath).values.map(mergeModules)
  }

  private[resolver] def moduleDescriptionsForTarget(target: BuildTarget,
                                                    scalacOptions: Option[ScalacOptionsItem],
                                                    depSourcesOpt: Option[DependencySourcesItem],
                                                    sourcesOpt: Option[SourcesItem],
                                                    dependencyOutputs: Seq[File]
                                                   )(implicit gson: Gson): Seq[ModuleDescription] = {

    val sourceItems: Seq[SourceItem] = (for {
      sources <- sourcesOpt.toSeq
      src <- sources.getSources.asScala
    } yield src).distinct

    val dependencySourcePaths = for {
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
    val sourceRoots = sourceDirs.filter { dir =>
      ! sourceDirs.exists(a => FileUtil.isAncestor(a.directory, dir.directory, true))
    }

    val moduleBase = Option(target.getBaseDirectory)
      .map(_.toURI.toFile)
      .orElse(commonBase(sourceRoots.map(_.directory)))

    val outputPath = scalacOptions.map(_.getClassDirectory.toURI.toFile)

    // classpath needs to be filtered for module dependency output paths since they are handled by IDEA module dep mechanism
    val classPath = scalacOptions.map(_.getClasspath.asScala.map(_.toURI.toFile))

    val classPathWithoutDependencyOutputs = classPath.getOrElse(Seq.empty).filterNot(dependencyOutputs.contains)

    val tags = target.getTags.asScala

    val targetData =
      if (tags.contains(BuildTargetTag.NO_IDE)) None
      else Option(target.getData).map(_.asInstanceOf[JsonElement])

    val scalaModule =
      targetData.flatMap(extractScalaSdkData)
        .map(target => getScalaSdkData(target, scalacOptions))
        .map(ScalaModule)

    // TODO there's ambiguity in the data object in BuildTarget.data
    //   there needs to be a marker for the type so that it can be deserialized to the correct class

    // TODO there's some disagreement on responsibility of handling sbt build data.
    //  specifically with bloop, the main workspace is not sbt-aware, and IntelliJ would need to start separate bloop
    //  servers for the build modules.
    //      val sbtModule =
    //        targetData.flatMap(extractSbtData)
    //          .map { data =>
    //            val (sbtModuleData, scalaSdkData) = calculateSbtData(data)
    //            SbtModule(scalaSdkData, sbtModuleData)
    //          }

    // TODO warning output when modules are skipped because of missing base or scala module data
    val moduleDescriptionOpt = for {
      baseDir <- moduleBase
      moduleKind <- scalaModule
    } yield {
      val moduleDescriptionData = createScalaModuleDescription(
        target, tags, baseDir, outputPath, sourceRoots,
        classPathWithoutDependencyOutputs, dependencySourcePaths)

      ModuleDescription(moduleDescriptionData, moduleKind)
    }

    moduleDescriptionOpt.toSeq
  }

  private[resolver] def createScalaModuleDescription(target: BuildTarget,
                                                     tags: Seq[String],
                                                     moduleBase: File,
                                                     outputPath: Option[File],
                                                     sourceRoots: Seq[SourceDirectory],
                                                     classPath: Seq[File],
                                                     dependencySources: Seq[File]
                                                    ): ModuleDescriptionData = {
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


  /** Merge modules assuming they have the same base path. */
  private[resolver] def mergeModules(descriptions: Seq[ModuleDescription]): ModuleDescription = {
    descriptions.reduce { (combined, next) =>
      val dataCombined = combined.data
      val dataNext = next.data
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


  private[resolver] def projectNode(projectRootPath: String,
                                    moduleFilesDirectoryPath: String,
                                    moduleDescriptions: Iterable[ModuleDescription]): DataNode[ProjectData] = {

    val projectRoot = new File(projectRootPath)
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
      (moduleDescription.data.targets, createModuleNode(projectRootPath, moduleFilesDirectoryPath, moduleDescription, projectNode))
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

  private[resolver] def createModuleNode(projectRootPath: String,
                                         moduleFilesDirectoryPath: String,
                                         moduleDescription: ModuleDescription,
                                         projectNode: DataNode[ProjectData]): DataNode[ModuleData] = {

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

  private[resolver] def createModuleDependencies(moduleDescriptions: Iterable[ModuleDescription], idToModule: Map[String, DataNode[ModuleData]]):
  Iterable[(DataNode[ModuleData], Seq[DataNode[ModuleData]])] = {
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

  private[resolver] def addNodeKindData(moduleNode: DataNode[ModuleData], moduleKind: ModuleKind): Unit = {
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

}
