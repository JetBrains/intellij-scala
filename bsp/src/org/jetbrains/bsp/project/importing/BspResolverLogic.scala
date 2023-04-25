package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j._
import com.google.gson.{Gson, JsonElement}
import com.intellij.openapi.externalSystem.model.project._
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileSystem
import org.jetbrains.bsp.BspUtil._
import org.jetbrains.bsp.data._
import org.jetbrains.bsp.project.BspSyntheticModuleType
import org.jetbrains.bsp.project.importing.BspResolverDescriptors._
import org.jetbrains.bsp.{BSP, BspBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, StringExt}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.external.{JdkByHome, JdkByVersion}
import org.jetbrains.sbt.project.data.MyURI
import org.jetbrains.sbt.project.module.SbtModuleType

import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.Collections
import scala.collection.mutable
import scala.jdk.CollectionConverters._

private[importing] object BspResolverLogic {

  private def extractJdkData(data: JsonElement)(implicit gson: Gson): Option[JvmBuildTarget] =
    Option(gson.fromJson[JvmBuildTarget](data, classOf[JvmBuildTarget]))

  private def extractScalaSdkData(data: JsonElement)(implicit gson: Gson): Option[ScalaBuildTarget] =
    Option(gson.fromJson[ScalaBuildTarget](data, classOf[ScalaBuildTarget]))

  private def extractSbtData(data: JsonElement)(implicit gson: Gson): Option[SbtBuildTarget] =
    Option(gson.fromJson[SbtBuildTarget](data, classOf[SbtBuildTarget]))

  private[importing] def getJdkData(target: JvmBuildTarget): JdkData = {
    val javaHome = Option(target.getJavaHome).map(new URI(_))

    JdkData(javaHome.map(new MyURI(_)).orNull, target.getJavaVersion)
  }

  private[importing] def getScalaSdkData(target: ScalaBuildTarget, scalacOptionsItem: Option[ScalacOptionsItem]): (JdkData, ScalaSdkData) = {
    val jdk = Option(target.getJvmBuildTarget).fold(JdkData(null, null))(getJdkData)

    val scalaOptionsStrings = scalacOptionsItem.map(item => item.getOptions).getOrElse(Collections.emptyList())
    val scala = ScalaSdkData(
      scalaOrganization = target.getScalaOrganization,
      scalaVersion = target.getScalaVersion,
      scalacClasspath = target.getJars.asScala.map(_.toURI.toFile).asJava,
      scaladocExtraClasspath = Collections.emptyList(), // FIXME pass in actual data when obtainable from BSP: https://github.com/build-server-protocol/build-server-protocol/issues/229
      scalacOptions = scalaOptionsStrings
    )
    (jdk, scala)
  }

  private[importing] def getSbtBuildModuleData(
    targetId: URI,
    target: SbtBuildTarget,
    scalacOptionsItem: Option[ScalacOptionsItem]
  ): (JdkData, ScalaSdkData, SbtBuildModuleDataBsp) = {
    val children = target.getChildren.asScala.map { target => new MyURI(target.getUri) }

    val sbtBuildModuleData = SbtBuildModuleDataBsp(
      id = new MyURI(targetId),
      imports = target.getAutoImports,
      childrenIds = children.asJava,
      sbtVersion = target.getSbtVersion
    )
    val (jdkData, scalaSdkData) = getScalaSdkData(target.getScalaBuildTarget, scalacOptionsItem)

    (jdkData, scalaSdkData, sbtBuildModuleData)
  }

  private[importing] def calculateModuleDescriptions(buildTargets: Seq[BuildTarget],
                                                     scalacOptionsItems: Seq[ScalacOptionsItem],
                                                     javacOptionsItems: Seq[JavacOptionsItem],
                                                     sourcesItems: Seq[SourcesItem],
                                                     resourcesItems: Seq[ResourcesItem],
                                                     outputPathsItems: Seq[OutputPathsItem],
                                                     dependencySourcesItems: Seq[DependencySourcesItem]): ProjectModules = {

    val idToTarget = buildTargets.map(t => (t.getId, t)).toMap
    val idToScalacOptions = scalacOptionsItems.map(item => (item.getTarget, item)).toMap
    val idToJavacOptions = javacOptionsItems.map(item => (item.getTarget, item)).toMap

    val transitiveDepComputed: mutable.Map[BuildTarget, Set[BuildTarget]] = mutable.Map.empty

    def transitiveDependencyOutputs(start: BuildTarget): Seq[File] = {
      val transitiveDeps = transitiveDependencies(start).map(_.getId)
      val scalaDeps = transitiveDeps.flatMap(idToScalacOptions.get).map(_.getClassDirectory.toURI.toFile).toSeq
      val javaDeps = transitiveDeps.flatMap(idToJavacOptions.get).map(_.getClassDirectory.toURI.toFile).toSeq
      (scalaDeps ++ javaDeps).sorted.distinct
    }

    def transitiveDependencies(start: BuildTarget): Set[BuildTarget] =
      transitiveDepComputed.getOrElseUpdate(start, {
        val direct = start.getDependencies.asScala.flatMap(idToTarget.get) // TODO warning when dependencies are not in buildTargets
        (start +: direct.flatMap(transitiveDependencies)).toSet
      })

    val idToDepSources = dependencySourcesItems
      .map(item => (item.getTarget, item.getSources.asScala.iterator.map(_.toURI.toFile).toSeq))
      .toMap

    val idToResources = resourcesItems
      .map(item => (item.getTarget, item.getResources.asScala.iterator.map(sourceDirectory(_)).distinct.toSeq))
      .toMap

    val idToOutputPaths = outputPathsItems
      .map(item => (item.getTarget, item.getOutputPaths.asScala.iterator.map(_.getUri.toURI.toFile).toSeq))
      .toMap

    // Source roots (repoted by BSP) that are not shared with other targets
    val exclusiveSourceRoots = sourcesItems
      .flatMap(item => item.getRoots.asScala.map(_ -> item.getTarget))
      .groupBy(_._1).view.mapValues(_.map(_._2).toSet)
      .filter(_._2.size == 1)
      .keys
      .toSet

    val idToSources = sourcesItems
      .map(item => (item.getTarget, sourceDirectories(item, idToTarget(item.getTarget), exclusiveSourceRoots)))
      .toMap

    val sharedResources = sharedSourceDirs(idToResources)
    val sharedSources = sharedSourceDirs(idToSources.view.mapValues(_.filterNot(_.generated)).toMap)
    val sharedGeneratedSources = idToSources
      .view
      .mapValues(_.filter(_.generated))
      .filter { case (id, src) => sharedSources.values.flatten.toSeq.contains(id) && src.nonEmpty }

    val moduleDescriptions = buildTargets.flatMap { (target: BuildTarget) =>
      val id = target.getId
      val scalacOptions = idToScalacOptions.get(id)
      val javacOptions = idToJavacOptions.get(id)
      val depSources = idToDepSources.getOrElse(id, Seq.empty)
      val sharedSourcesAndGenerated = (sharedSources.keys ++ sharedGeneratedSources.values.flatten).toSeq
      val sources = idToSources.getOrElse(id, Seq.empty).filterNot(sharedSourcesAndGenerated.contains)
      val resources = idToResources.getOrElse(id, Seq.empty).filterNot(sharedResources.contains)
      val outputPaths = idToOutputPaths.getOrElse(id, Seq.empty)
      val dependencyOutputs = transitiveDependencyOutputs(target)

      implicit val gson: Gson = new Gson()
      moduleDescriptionForTarget(target, scalacOptions, javacOptions, depSources, sources, resources, outputPaths, dependencyOutputs)
    }

    val idToModule = (for {
      m <- moduleDescriptions
      t <- m.data.targets
    } yield (t.getId, m)).toMap

    val targetIdsResources = sharedResources.toSeq
      .groupBy(_._2.sortBy(_.getUri))
      .view
      .mapValues(_.map(_._1))
      .toSeq
      .sortBy(_._1.size)

    val idsGeneratedSources = sharedSources.values.toSeq.distinct
      .sortBy(_.size)
      .foldRight((sharedGeneratedSources, Map.empty[Seq[BuildTargetIdentifier], Seq[SourceDirectory]])) {
        case (ids, (sharedGeneratedSources, result)) =>
          val sharedGeneratedSourcesForIds = sharedGeneratedSources.filterKeys(ids.contains)
          (
            sharedGeneratedSources.filterKeys(!sharedGeneratedSourcesForIds.keySet.contains(_)),
            result + (ids.sortBy(_.getUri) -> sharedGeneratedSourcesForIds.values.flatten.toSeq)
          )
      }._2

    val syntheticSourceModules = sharedSources.toSeq
      .groupBy(_._2.sortBy(_.getUri))
      .view
      .mapValues(_.map(_._1))
      .toSeq
      .map { case (targetIds, sources) =>
        val targets = targetIds.map(idToTarget)
        val sharingModules = targetIds.map(idToModule)
        val resources = targetIdsResources.find(_._1.diff(targetIds).isEmpty).toSeq.flatMap(_._2)
        val genSources = idsGeneratedSources.get(targetIds).toSeq.flatten
        createSyntheticModuleDescription(targets, resources, sources, genSources, sharingModules)
      }

    // merge modules with the same module base
    val (noBase, withBase) = moduleDescriptions.partition(_.data.basePath.isEmpty)
    val mergedBase = withBase.groupBy(_.data.basePath).values.map(mergeModules)
    val modules: Seq[ModuleDescription] = noBase ++ mergedBase

    val (sbtBuildModules, ordinaryModules) =
      modules.partition(_.moduleKindData.is[BspResolverDescriptors.ModuleKind.SbtModule])

    //First register "non-build" modules and then register "build" modules
    //This is just in case there are any unexpected duplicated content roots issues.
    //We want normal modules to work properly (see e.g. SCL-19673)
    ProjectModules(ordinaryModules ++ sbtBuildModules, syntheticSourceModules)
  }

  private def sharedSourceDirs(idToSources: Map[BuildTargetIdentifier, Seq[SourceDirectory]]): Map[SourceDirectory, Seq[BuildTargetIdentifier]] = {
    val idToSrc = for {
      (id, sources) <- idToSources.toSeq
      dir <- sources
    } yield (id, dir)

    idToSrc
      .groupBy(_._2) // TODO merge source dirs with mixed generated flag?
      .map { case (dir, derp) => (dir, derp.map(_._1)) }
      .filter(_._2.size > 1)
  }

  private def sourceDirectories(
    sourcesItem: SourcesItem,
    target: BuildTarget,
    exclusiveSourceRoots: Set[String]
  ): Seq[SourceDirectory] = {
    val sourceRoots = Option(sourcesItem.getRoots).map(_.asScala.toSeq).getOrElse(Seq.empty)

    // Get source root as reported by BSP if it is exclusive to this target
    // and is under the target base directory.
    // This approach avoids creating more source roots than necessary.
    // Example:
    // src/test/scala/org/a/MyTest.scala
    // src/test/scala/org/b/MyTest.scala
    // By default two source roots would be created here, which makes it harder
    // to for example run all tests in target in IntelliJ.
    val targetBasePath: Option[Path] = Option(target.getBaseDirectory).map(_.toURI.toPath)
    val allowedSourceRootsFromBsp = sourceRoots.filter(exclusiveSourceRoots).map(_.toURI.toPath)

    def getSourceRootFromBsp(item: SourceItem): Option[File] = {
      for {
        targetBase <- targetBasePath
        sourcePath = item.getUri.toURI.toPath
        sourceRoot <- allowedSourceRootsFromBsp.find(sourcePath.startsWith)
        if sourceRoot.startsWith(targetBase)
      } yield sourceRoot.toFile
    }

    val sourceItems: Seq[SourceItem] = sourcesItem.getSources.asScala.iterator.distinct.toSeq
    val directories = sourceItems
      .map { item =>
        getSourceRootFromBsp(item) match {
          case Some(sourceRoot) =>
            SourceDirectory(sourceRoot, item.getGenerated, packagePrefix = None)
          case None =>
            val packagePrefix = findPackagePrefix(sourceRoots, item.getUri)
            val file = item.getUri.toURI.toFile
            // bsp spec used to depend on uri ending in `/` to determine directory, use both kind and uri string to determine directory
            if (item.getKind == SourceItemKind.DIRECTORY || item.getUri.endsWith("/"))
              SourceDirectory(file, item.getGenerated, packagePrefix)
            else
            // use the file's immediate parent as best guess of source dir
            // IntelliJ project model doesn't have a concept of individual source files
              SourceDirectory(file.getParentFile, item.getGenerated, packagePrefix)
        }
      }
    directories.distinct
  }

  private def findPackagePrefix(roots: Seq[String], sourceUri: String): Option[String] = {
    val matchedRoot = roots.find(root => sourceUri.startsWith(root))
    matchedRoot.map { root =>
      val rootPath = root.toURI.toPath
      val filePath = sourceUri.toURI.toPath
      val dirPath = if (sourceUri.endsWith("/")) filePath else filePath.getParent
      val relativePath = rootPath.relativize(dirPath)
      relativePath.toString.replace(File.separatorChar, '.')
    }.filter(_.nonEmpty)
  }

  private def sourceDirectory(uri: String, generated: Boolean = false) = {
    val file = uri.toURI.toFile
    if (uri.endsWith("/")) SourceDirectory(file, generated, None)
    else SourceDirectory(file.getParentFile, generated, None)
  }

  /**
   * @return list of directories from `dirs` which are not child directories of any other directory in `dirs`<br>
   * @note in IntelliJ all subdirectories of a source directory are automatically source dirs<br>
   *       So there is no need to register child directories as source directories.
   * @example {{{
   * input:
   *    basePath1
   *    basePath1/src/main/scala
   *    basePath1/src/main/scala-2.12
   *    basePath1/src/main/scala-2
   *    basePath1/src/main/java
   *    basePath1/src/main/scala-sbt-1.0
   *    basePath1/target/scala-2.12/sbt-1.0/src_managed/main
   *    basePath2
   *    basePath2/src/main/scala
   *    basePath2/src/main/scala-2.12
   *  output:
   *    basePath1
   *    basePath2
   * }}}
   */
  private def excludeChildDirectories(dirs: Seq[SourceDirectory]) =
    dirs.filter { dir =>
      !dirs.exists(a => FileUtil.isAncestor(a.directory, dir.directory, true))
    }

  private[importing] def moduleDescriptionForTarget(buildTarget: BuildTarget,
                                                    scalacOptions: Option[ScalacOptionsItem],
                                                    javacOptions: Option[JavacOptionsItem],
                                                    dependencySourceDirs: Seq[File],
                                                    sourceDirs: Seq[SourceDirectory],
                                                    resourceDirs: Seq[SourceDirectory],
                                                    outputPaths: Seq[File],
                                                    dependencyOutputs: Seq[File]
                                                  )(implicit gson: Gson): Option[ModuleDescription] = {
    val moduleBaseDir: Option[File] = Option(buildTarget.getBaseDirectory).map(_.toURI.toFile)
    val outputPath =
      scalacOptions.map(_.getClassDirectory.toURI.toFile)
        .orElse(javacOptions.map(_.getClassDirectory.toURI.toFile))

    // classpath needs to be filtered for module dependency output paths since they are handled by IDEA module dep mechanism
    val scalaClassPath = scalacOptions.map(_.getClasspath.asScala.iterator.map(_.toURI.toFile)).getOrElse(Iterator.empty)
    val javaClassPath = javacOptions.map(_.getClasspath.asScala.iterator.map(_.toURI.toFile)).getOrElse(Iterator.empty)
    val classPath = (scalaClassPath ++ javaClassPath).toSeq.sorted.distinct

    val classPathWithoutDependencyOutputs = classPath.filterNot(dependencyOutputs.contains)

    val tags = buildTarget.getTags.asScala

    val targetData = Option(buildTarget.getData).map(_.asInstanceOf[JsonElement])
    val langLevel = javacOptions
      .flatMap(_.getOptions.asScala.dropWhile(_ != "-source").drop(1).headOption)
      .map(LanguageLevel.parse)
      .flatMap(Option(_))
    val moduleKind: Option[ModuleKind] = targetData.flatMap { _ =>
      import ModuleKind._
      buildTarget.getDataKind match {
        case BuildTargetDataKind.JVM =>
          targetData.flatMap(extractJdkData)
            .map(target => getJdkData(target))
            .map(JvmModule.apply)
        case BuildTargetDataKind.SCALA =>
          targetData.flatMap(extractScalaSdkData)
            .map(target => getScalaSdkData(target, scalacOptions))
            .map((ScalaModule.apply _).tupled)
        case BuildTargetDataKind.SBT =>
          val buildTargetId = new URI(buildTarget.getId.getUri)
          targetData.flatMap(extractSbtData)
            .map(target => getSbtBuildModuleData(buildTargetId, target, scalacOptions))
            .map((SbtModule.apply _).tupled)
        case _ =>
          Some(UnspecifiedModule())
      }
    }

    /**
     * Do not mark project root as a content root for "build" modules.
     *
     * NOTE: for "build" modules BSP reports project root as one of the source directories<br>
     * While it's technically true (project root contains e.g. `build.sbt`), we can't register it as a content root
     * because it already belongs to the "main" ("non-build") module.
     * IntelliJ will remove duplicated content roots:<br>
     * (see [[com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService.filterAndReportDuplicatingContentRoots]])
     *
     * We could workaround this by first registering non-build modules and then build modules.
     * This would work, but still users would get an annoying notification "Duplicated content roots found" for "build" modules.
     *
     * @note though `build.sbt` file is not located in the "build" module content root (but in the main module content root)
     *       we have several workarounds to substitute the correct "build" module<br>
     *       Examples:
     *       - [[org.jetbrains.sbt.language.SbtFile.findBuildModule]]
     */
    val (sourceDirsFiltered1, resourceDirsFiltered1) = (moduleBaseDir, moduleKind) match {
      case (Some(baseDir), Some(_: BspResolverDescriptors.ModuleKind.SbtModule)) =>
        (
          sourceDirs.filter(f => FileUtil.isAncestor(baseDir, f.directory, false)),
          resourceDirs.filter(f => FileUtil.isAncestor(baseDir, f.directory, false))
        )
      case _ =>
        (sourceDirs, resourceDirs)
    }

    val (sourceDirsFiltered2, resourceDirsFiltered2) = (
      excludeChildDirectories(sourceDirsFiltered1),
      excludeChildDirectories(resourceDirsFiltered1)
    )

    val moduleDescriptionData: ModuleDescriptionData = createModuleDescriptionData(
      target = buildTarget,
      tags = tags.toSeq,
      moduleBase = moduleBaseDir,
      outputPath = outputPath,
      sourceRoots = sourceDirsFiltered2,
      resourceRoots = resourceDirsFiltered2,
      classPath = classPathWithoutDependencyOutputs,
      dependencySources = dependencySourceDirs,
      outputPaths = outputPaths,
      languageLevel = langLevel
    )

    if (tags.contains(BuildTargetTag.NO_IDE)) None
    else Option(ModuleDescription(moduleDescriptionData, moduleKind.getOrElse(ModuleKind.UnspecifiedModule())))
  }

  private[importing] def createModuleDescriptionData(target: BuildTarget,
                                                     tags: Seq[String],
                                                     moduleBase: Option[File],
                                                     outputPath: Option[File],
                                                     sourceRoots: Seq[SourceDirectory],
                                                     resourceRoots: Seq[SourceDirectory],
                                                     outputPaths: Seq[File],
                                                     classPath: Seq[File],
                                                     dependencySources: Seq[File],
                                                     languageLevel: Option[LanguageLevel]
                                                    ): ModuleDescriptionData = {
    import BuildTargetTag._

    val moduleId = target.getId.getUri
    val moduleName = target.getDisplayName

    val dataBasic = ModuleDescriptionData(
      idUri = moduleId,
      name = moduleName,
      targets = Seq(target),
      targetDependencies = Seq.empty, targetTestDependencies = Seq.empty,
      basePath = moduleBase,
      output = None, testOutput = None,
      sourceDirs = Seq.empty, testSourceDirs = Seq.empty,
      resourceDirs = Seq.empty, testResourceDirs = Seq.empty,
      outputPaths = outputPaths,
      classpath = Seq.empty, classpathSources = Seq.empty,
      testClasspath = Seq.empty, testClasspathSources = Seq.empty, languageLevel = languageLevel)

    val targetDeps = target.getDependencies.asScala.toSeq

    val data = if (tags.contains(TEST))
      dataBasic.copy(
        targetTestDependencies = targetDeps,
        testOutput = outputPath,
        testSourceDirs = sourceRoots,
        testResourceDirs = resourceRoots,
        testClasspath = classPath,
        testClasspathSources = dependencySources
      ) else
      dataBasic.copy(
        targetDependencies = targetDeps,
        output = outputPath,
        sourceDirs = sourceRoots,
        resourceDirs = resourceRoots,
        classpath = classPath,
        classpathSources = dependencySources
      )

    data
  }

  //IntelliJ may attempt to append " (shared)" to the file name, putting it back over the max limit
  //so we subtract 50 characters just in this case
  private final val MaxFileNameLength = FileSystem.getCurrent.getMaxFileNameLength - 50

  private[importing] case class TargetIdAndName(idUri: String, name: String)

  private[importing] def sharedModuleTargetIdAndName(targets: Seq[BuildTarget]): TargetIdAndName = {
    val shortId = sharedModuleShortId(targets)
    //using URI constructor just to assert URI syntax is valid
    val targetId = new URI(s"file:/dummyPathForSharedSourcesModule?id=$shortId").toString
    val name = shortId + " (shared)"
    TargetIdAndName(targetId, name)
  }

  private def sharedModuleShortId(targets: Seq[BuildTarget]): String = {
    val upperCaseWords = """(?<!(^|[A-Z]))(?=[A-Z])""".r
    val pascalCaseWords = """(?<!^)(?=[A-Z][a-z])""".r
    val underscores = """(?<=[^\w.]|_)|(?=[^\w.]|_)""".r
    val dotsAndDigits = """(?<!\d)(?=\.)|(?<=\.)(?!\d)""".r
    val splitedNames = targets
      .map(_.getDisplayName.split(s"$upperCaseWords|$pascalCaseWords|$underscores|$dotsAndDigits"))
    val maxPartsCount = splitedNames.map(_.length).max
    val groups = splitedNames
      .map(parts => parts ++ Seq.fill(maxPartsCount - parts.length)(""))
      .transpose
      .map(_.distinct)
    val (head, tail) = groups.partition(_.forall(_.nonEmpty))

    def combine(parts: Seq[String]) = {
      val nonEmptyParts = parts.filter(_.nonEmpty)
      if (nonEmptyParts.size > 1) nonEmptyParts.mkString("(", "+", ")") else nonEmptyParts.mkString
    }

    val ret = head.map(combine).mkString +
      (if (tail.nonEmpty) tail.map(combine).mkString("(", "", ")") else tail.mkString)
    if (ret.length > MaxFileNameLength) {
      val suffix = DigestUtils.md5Hex(ret)
      val prefix = ret.substring(0, MaxFileNameLength - suffix.length)
      prefix + suffix
    } else {
      ret
    }
  }

  /** "Inherits" data from other modules into newly created synthetic module description.
   * This is a heuristic to for sharing source directories between modules. If those modules have conflicting dependencies,
   * this mapping may break in unspecified ways.
   */
  private[importing] def createSyntheticModuleDescription(targets: Seq[BuildTarget],
                                                          resources: Seq[SourceDirectory],
                                                          sourceRoots: Seq[SourceDirectory],
                                                          generatedSourceRoots: Seq[SourceDirectory],
                                                          ancestors: Seq[ModuleDescription]): ModuleDescription = {
    // the synthetic module "inherits" most of the "ancestors" data
    val merged = mergeModules(ancestors)
    val TargetIdAndName(idUri, name) = sharedModuleTargetIdAndName(targets)
    val sources = sourceRoots ++ generatedSourceRoots
    val isTest = targets.exists(_.getTags.asScala.contains(BuildTargetTag.TEST))

    val inheritorData = merged.data.copy(
      idUri = idUri,
      name = name,
      targets = targets,
      resourceDirs = resources,
      sourceDirs = if (isTest) Seq.empty else sources,
      testSourceDirs = if (isTest) sources else Seq.empty,
      basePath = None
    )
    merged.copy(data = inheritorData)
  }


  /** Merge modules assuming they have the same base path. */
  private[importing] def mergeModules(descriptions: Seq[ModuleDescription]): ModuleDescription = {
    descriptions
      .sortBy(_.data.idUri)
      .reduce { (combined, next) =>
        val dataCombined = combined.data
        val dataNext = next.data
        val targets = (dataCombined.targets ++ dataNext.targets).sortBy(_.getId.getUri).distinct
        val targetDependencies = mergeBTIs(dataCombined.targetDependencies, dataNext.targetDependencies)
        val targetTestDependencies = mergeBTIs(dataCombined.targetTestDependencies, dataNext.targetTestDependencies)
        val output = dataCombined.output.orElse(dataNext.output)
        val testOutput = dataCombined.testOutput.orElse(dataNext.testOutput)
        val sourceDirs = mergeSourceDirs(dataCombined.sourceDirs, dataNext.sourceDirs)
        val resourceDirs = mergeSourceDirs(dataCombined.resourceDirs, dataNext.resourceDirs)
        val testResourceDirs = mergeSourceDirs(dataCombined.testResourceDirs, dataNext.testResourceDirs)
        val testSourceDirs = mergeSourceDirs(dataCombined.testSourceDirs, dataNext.testSourceDirs)
        val outputPaths = mergeFiles(dataCombined.outputPaths, dataNext.outputPaths)
        val classPath = mergeFiles(dataCombined.classpath, dataNext.classpath)
        val classPathSources = mergeFiles(dataCombined.classpathSources, dataNext.classpathSources)
        val testClassPath = mergeFiles(dataCombined.testClasspath, dataNext.testClasspath)
        val testClassPathSources = mergeFiles(dataCombined.testClasspathSources, dataNext.testClasspathSources)
        val languageLevel = (dataCombined.languageLevel ++ dataNext.languageLevel).maxOption

        val newData = ModuleDescriptionData(
          idUri = dataCombined.idUri, name = dataCombined.name,
          targets = targets, targetDependencies = targetDependencies, targetTestDependencies = targetTestDependencies, basePath = dataCombined.basePath,
          output = output, testOutput = testOutput,
          sourceDirs = sourceDirs, testSourceDirs = testSourceDirs,
          resourceDirs = resourceDirs, testResourceDirs = testResourceDirs,
          outputPaths = outputPaths,
          classpath = classPath, classpathSources = classPathSources,
          testClasspath = testClassPath, testClasspathSources = testClassPathSources, languageLevel = languageLevel)

        val newModuleKindData = mergeModuleKind(combined.moduleKindData, next.moduleKindData)

        combined.copy(newData, newModuleKindData)
      }
  }

  private def mergeBTIs(a: Seq[BuildTargetIdentifier], b: Seq[BuildTargetIdentifier]) =
    (a ++ b).sortBy(_.getUri).distinct

  private def mergeSourceDirs(a: Seq[SourceDirectory], b: Seq[SourceDirectory]) =
    (a ++ b).sortBy(_.directory.getAbsolutePath).distinct

  private def mergeFiles(a: Seq[File], b: Seq[File]) =
    (a ++ b).sortBy(_.getAbsolutePath).distinct

  private def mergeModuleKind(a: ModuleKind, b: ModuleKind) = {
    import ModuleKind._
    (a, b) match {
      case (UnspecifiedModule(), other) => other
      case (other, UnspecifiedModule()) => other
      case (module1@ScalaModule(_, data1), module2@ScalaModule(_, data2)) =>
        if (Version(data1.scalaVersion) >= Version(data2.scalaVersion))
          module1
        else
          module2
      case (first, _) => first
    }
  }

  // extension method added as for better performance than getCanonicalPath method
  implicit class FileExtensions(val f: File) {
    def getCanonicalPathOptimized: String = {
      f.toPath.toAbsolutePath.normalize().toString
    }

    def getCanonicalFileOptimized: File = {
      f.toPath.toAbsolutePath.normalize().toFile
    }
  }

  private val jarSuffix = ".jar"
  private val sourcesSuffixes = Seq("-sources", "-src")
  private val javadocSuffix = "-javadoc"

  private def stripSuffixes(path: String): String =
    path
      .stripSuffix(jarSuffix)
      .stripSuffixes(sourcesSuffixes)
      .stripSuffix(javadocSuffix)

  private def libraryPrefix(path: File): Option[String] =
    if (path.getName.endsWith(jarSuffix))
      Option(stripSuffixes(path.getCanonicalPathOptimized))
    else None

  private def libraryName(path: File): String =
    stripSuffixes(path.getName)

  private[importing] def projectNode(
    workspace: File,
    projectModules: ProjectModules,
    excludedPaths: List[File],
    displayName: String
  ): DataNode[ProjectData] = {

    val projectRootPath = workspace.getCanonicalPathOptimized
    val moduleFileDirectoryPath = moduleFilesDirectory(workspace).getCanonicalPathOptimized
    val projectRoot = new File(projectRootPath)
    val projectData = new ProjectData(BSP.ProjectSystemId, projectRoot.getName, projectRootPath, projectRootPath)
    val projectNode = new DataNode[ProjectData](ProjectKeys.PROJECT, projectData, null)

    // synthetic root module when no natural module is at root
    val rootModule =
      if (projectModules.modules.exists(_.data.basePath.exists(_ == projectRoot))) None
      else {
        val name = projectRoot.getName + "-root"
        val moduleData = new ModuleData(name, BSP.ProjectSystemId, BspSyntheticModuleType.Id, name, moduleFileDirectoryPath, projectRootPath)
        val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)
        val contentRootData = new ContentRootData(BSP.ProjectSystemId, projectRoot.getCanonicalPathOptimized)
        val contentRootDataNode = new DataNode[ContentRootData](ProjectKeys.CONTENT_ROOT, contentRootData, moduleNode)
        moduleNode.addChild(contentRootDataNode)

        Some(moduleNode)
      }

    val projectLibraryDependencies: Map[TestClassId, LibraryData] =
      projectModules.modules.toSet
        .flatMap((m: ModuleDescription) =>
          m.data.classpath ++
            m.data.classpathSources ++
            m.data.testClasspath ++
            m.data.testClasspathSources)
        .map(_.getCanonicalFileOptimized)
        .groupBy(libraryPrefix)
        // ignore non-standard jar libs
        .flatMap { case (pathPrefix, jars) => pathPrefix.map(p => p -> jars) }
        .groupBy { case (_, files) => libraryName(files.head) }
        .flatMap { case name -> prefixLibs =>
          if (prefixLibs.size == 1) {
            // use short name when unique
            prefixLibs.map { case prefix -> files => prefix -> (name, files) }
          }
          else {
            // add a number for uniqueness and to keep names short
            prefixLibs.toList.sortBy(_._1).zipWithIndex
              .map { case (prefix -> files, index) => prefix -> (s"${name}__${index + 1}", files) }
          }
        }
        .flatMap { case (pathPrefix, (name, jars)) =>
          val binary = jars.find(_.toString.endsWith(pathPrefix + jarSuffix))
          val source = jars.find(j => sourcesSuffixes.exists(j.getName.contains))
          val doc = jars.find(_.getName.contains(javadocSuffix))
          binary.map { bin =>
            val data = new LibraryData(BSP.ProjectSystemId, name)
            data.addPath(LibraryPathType.BINARY, bin.toString)
            source.foreach(src => data.addPath(LibraryPathType.SOURCE, src.toString))
            doc.foreach(doc => data.addPath(LibraryPathType.DOC, doc.toString))
            pathPrefix -> data
          }
        }

    val moduleIdToBuildModuleId: Map[MyURI, MyURI] = projectModules.modules
      .flatMap { moduleDescription =>
        moduleDescription.moduleKindData match {
          case ModuleKind.SbtModule(_, _, sbtData) =>
            sbtData.childrenIds.asScala.map(_ -> sbtData.id)
          case _ =>
            None
        }
      }
      .toMap

    def toModuleNode(
      moduleDescription: ModuleDescription,
    ): DataNode[ModuleData] =
      createModuleNode(
        projectRootPath,
        moduleFileDirectoryPath,
        moduleDescription,
        projectNode,
        projectLibraryDependencies,
        moduleIdToBuildModuleId
      )

    val idsToTargetModule: Seq[(Seq[TargetId], DataNode[ModuleData])] =
      projectModules.modules.map { m =>
        val targetIds = m.data.targets.map(t => TargetId(t.getId.getUri))
        val node = toModuleNode(m)
        targetIds -> node
      }

    val idToRootModule = rootModule.toSeq.map(m => SynthId(m.getData.getId) -> m)
    val idToSyntheticModule = projectModules.synthetic.map { m => SynthId(m.data.idUri) -> toModuleNode(m) }
    val idToTargetModule = idsToTargetModule.flatMap { case (ids, m) => ids.map(_ -> m) }
    val idToModuleMap: Map[DependencyId, DataNode[ModuleData]] =
      (idToRootModule ++ idToTargetModule ++ idToSyntheticModule).toMap


    val moduleDeps = calculateModuleDependencies(projectModules)
    val synthDeps = calculateSyntheticDependencies(moduleDeps, projectModules)
    val modules = idToModuleMap.values.toSet

    val bspProjectData = {
      val jdkReference = inferProjectJdk(modules)
      val vcsRootsCandidates = projectModules.modules.flatMap(_.data.basePath).distinct
      new DataNode[BspProjectData](BspProjectData.Key, BspProjectData(jdkReference, vcsRootsCandidates.asJava, displayName), projectNode)
    }

    // effects
    addModuleDependencies(moduleDeps ++ synthDeps, idToModuleMap)
    addRootExclusions(modules, projectRoot, excludedPaths)
    modules.foreach(projectNode.addChild)
    projectNode.addChild(bspProjectData)

    projectLibraryDependencies.values.foreach { data =>
      val node = new DataNode[LibraryData](ProjectKeys.LIBRARY, data, projectNode)
      projectNode.addChild(node)
    }

    projectNode
  }

  private def addRootExclusions(modules: Set[DataNode[ModuleData]], projectRoot: File, excludedPaths: List[File]): Unit =
    for {
      m <- modules.toList
      c <- m.getChildren.asScala
      data <- Option(c.getData(ProjectKeys.CONTENT_ROOT)).toList
      if new File(data.getRootPath) == projectRoot
      p <- excludedPaths
    } {
      data.storePath(ExternalSystemSourceType.EXCLUDED, p.getAbsolutePath)
    }

  private def inferProjectJdk(modules: Set[DataNode[ModuleData]]) = {
    val groupedJdks = modules
      .flatMap(m => Option(ExternalSystemApiUtil.find(m, BspMetadata.Key)))
      .map(m => (m.getData.javaHome, m.getData.javaVersion))
      .filter { case (home, version) => home != null || version != null }
      .groupBy(identity).view.mapValues(_.size)

    val jdkReference = if (groupedJdks.isEmpty) {
      None
    } else {
      val (home, version) = groupedJdks.maxBy { case (_, count) => count }._1
      Option(home).map(_.uri.toFile).map(JdkByHome).orElse(Option(version).map(JdkByVersion))
    }
    jdkReference
  }

  private[importing] def moduleFilesDirectory(workspace: File) = new File(workspace, ".idea/modules")

  private[importing] def createModuleNode(
    projectRootPath: String,
    moduleFileDirectoryPath: String,
    moduleDescription: ModuleDescription,
    projectNode: DataNode[ProjectData],
    projectLibraryDependencies: Map[String, LibraryData],
    buildTargetIdToBuildModuleTargetId: Map[MyURI, MyURI]
  ): DataNode[ModuleData] = {
    import ExternalSystemSourceType._

    val moduleDescriptionData = moduleDescription.data

    val moduleBase: Option[ContentRootData] = moduleDescriptionData.basePath.map { path =>
      val base = path.getCanonicalPathOptimized
      new ContentRootData(BSP.ProjectSystemId, base)
    }
    val sourceRoots = moduleDescriptionData.sourceDirs.map { dir =>
      val sourceType = if (dir.generated) SOURCE_GENERATED else SOURCE
      (sourceType, dir)
    }

    val resourceRoots = moduleDescriptionData.resourceDirs.map { dir =>
      (RESOURCE, dir)
    }

    val testRoots = moduleDescriptionData.testSourceDirs.map { dir =>
      val sourceType = if (dir.generated) TEST_GENERATED else TEST
      (sourceType, dir)
    }

    val testResourceRoots = moduleDescriptionData.testResourceDirs.map { dir =>
      (TEST_RESOURCE, dir)
    }

    val allSourceRoots: Seq[(ExternalSystemSourceType, SourceDirectory)] =
      sourceRoots ++ testRoots ++ resourceRoots ++ testResourceRoots

    val moduleName = moduleDescriptionData.name
    val moduleType = moduleDescription.moduleKindData match {
      case ModuleKind.SbtModule(_, _, _) =>
        SbtModuleType.instance
      case _ =>
        StdModuleTypes.JAVA
    }
    val moduleData = new ModuleData(moduleDescriptionData.idUri, BSP.ProjectSystemId, moduleType.getId, moduleName, moduleFileDirectoryPath, projectRootPath)

    moduleDescriptionData.output.foreach { outputPath =>
      moduleData.setCompileOutputPath(SOURCE, outputPath.getCanonicalPathOptimized)
    }
    moduleDescriptionData.testOutput.foreach { outputPath =>
      moduleData.setCompileOutputPath(TEST, outputPath.getCanonicalPathOptimized)
    }

    moduleData.setInheritProjectCompileOutputPath(false)


    def namedLibraries(classpath: Seq[File], sources: Seq[File]) = {
      val (named, other) = classpath
        .groupBy(libraryPrefix)
        .partitionMap {
          case Some(libName) -> _ => Left(libName)
          case None -> paths => Right(paths)
        }
      val otherSources = sources.filter { s => !libraryPrefix(s).exists(projectLibraryDependencies.contains) }
      (named, other.flatten, otherSources)
    }

    val (namedLibs, otherLibs, otherSources) =
      namedLibraries(moduleDescriptionData.classpath, moduleDescriptionData.classpathSources)
    val (namedTestLibs, otherTestLibs, otherTestSources) =
      namedLibraries(moduleDescriptionData.testClasspath, moduleDescriptionData.testClasspathSources)

    def configureNamedLibraryDependencyData(libPrefixes: Iterable[String], scope: DependencyScope) = libPrefixes
      .flatMap(projectLibraryDependencies.get)
      .map { libData =>
        val libDepData = new LibraryDependencyData(moduleData, libData, LibraryLevel.PROJECT)
        libDepData.setScope(scope)
        libDepData
      }

    val namedLibraryDependencyData =
      configureNamedLibraryDependencyData(namedLibs, DependencyScope.COMPILE) ++
        configureNamedLibraryDependencyData(namedTestLibs, DependencyScope.TEST)

    def configureLibraryDependencyData(name: String, scope: DependencyScope, libs: Iterable[File], sources: Iterable[File]) = {
      val libraryData = new LibraryData(BSP.ProjectSystemId, name)
      libs.foreach { path => libraryData.addPath(LibraryPathType.BINARY, path.getCanonicalPathOptimized) }
      sources.foreach { path => libraryData.addPath(LibraryPathType.SOURCE, path.getCanonicalPathOptimized) }
      val libraryDependencyData = new LibraryDependencyData(moduleData, libraryData, LibraryLevel.MODULE)
      libraryDependencyData.setScope(scope)
      libraryDependencyData
    }

    val libraryDataName =
      BspResolverNamingExtension.libraryData(moduleDescription)
        .getOrElse(BspBundle.message("bsp.resolver.modulename.dependencies", moduleName))
    val libraryTestDataName =
      BspResolverNamingExtension.libraryTestData(moduleDescription)
        .getOrElse(BspBundle.message("bsp.resolver.modulename.test.dependencies", moduleName))

    val libraryDependencyData =
      configureLibraryDependencyData(libraryDataName, DependencyScope.COMPILE, otherLibs, otherSources)
    val libraryTestDependencyData =
      configureLibraryDependencyData(libraryTestDataName, DependencyScope.TEST, otherTestLibs, otherTestSources)


    // data node wiring

    val moduleNode = new DataNode[ModuleData](ProjectKeys.MODULE, moduleData, projectNode)

    namedLibraryDependencyData
      .map { data => new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, data, moduleNode) }
      .foreach(moduleNode.addChild)

    val libraryDependencyNode =
      new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData, moduleNode)
    moduleNode.addChild(libraryDependencyNode)
    val libraryTestDependencyNode =
      new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, libraryTestDependencyData, moduleNode)
    moduleNode.addChild(libraryTestDependencyNode)

    val contentRootData: Iterable[ContentRootData] = {
      val rootPathToData: Seq[(String, ContentRootData)] = allSourceRoots.map { case (sourceType, root) =>
        val data = getContentRoot(root.directory, moduleBase)
        data.storePath(sourceType, root.directory.getCanonicalPathOptimized, root.packagePrefix.orNull)
        data.getRootPath -> data
      }
      // effectively deduplicate by content root path. ContentRootData does not implement equals correctly
      rootPathToData.toMap.values
    }
    contentRootData.foreach { data =>
      val contentRootDataNode = new DataNode[ContentRootData](ProjectKeys.CONTENT_ROOT, data, moduleNode)
      moduleNode.addChild(contentRootDataNode)
    }

    moduleDescription.data.outputPaths.foreach { outputPath =>
      val data = getContentRoot(outputPath, moduleBase)
      data.storePath(EXCLUDED, outputPath.getCanonicalPathOptimized)
    }

    val metadata = createBspMetadata(moduleDescription)
    val metadataNode = new DataNode[BspMetadata](BspMetadata.Key, metadata, moduleNode)
    moduleNode.addChild(metadataNode)

    addNodeKindData(moduleNode, moduleDescription.moduleKindData, moduleDescription.data, buildTargetIdToBuildModuleTargetId)

    moduleNode
  }

  private def createBspMetadata(moduleDescription: ModuleDescription): BspMetadata = {
    val targetIds = moduleDescription.data.targets.map(t => new MyURI(t.getId.getUri))
    import ModuleKind._
    val jdkData = moduleDescription.moduleKindData match {
      case module: JvmModule => Some(module.jdkData)
      case module: ScalaModule => Some(module.jdkData)
      case module: SbtModule => Some(module.jdkData)
      case _ => None
    }
    jdkData.fold(BspMetadata(targetIds.asJava, null, null, null)) { data =>
      BspMetadata(targetIds.asJava, data.javaHome, data.javaVersion, moduleDescription.data.languageLevel.orNull)
    }
  }

  /** Use moduleBase content root when possible, or create a new content root if dir is not within moduleBase. */
  private[importing] def getContentRoot(dir: File, moduleBase: Option[ContentRootData]) = {
    val baseRoot = for {
      contentRoot <- moduleBase
      if FileUtil.isAncestor(contentRoot.getRootPath, dir.getCanonicalPathOptimized, false)
    } yield contentRoot

    baseRoot.getOrElse(new ContentRootData(BSP.ProjectSystemId, dir.getCanonicalPathOptimized))
  }


  private[importing] def calculateModuleDependencies(projectModules: ProjectModules): Seq[ModuleDep] = for {
    moduleDescription <- projectModules.modules
    moduleTargets = moduleDescription.data.targets
    aTarget <- moduleTargets.headOption.toSeq // any id will resolve the module in idToModule
    d <- {
      val moduleId = aTarget.getId.getUri
      val compileDeps = moduleDescription.data.targetDependencies.map((_, DependencyScope.COMPILE))
      val testDeps = moduleDescription.data.targetTestDependencies.map((_, DependencyScope.TEST))

      (compileDeps ++ testDeps)
        .filterNot(d => moduleTargets.exists(t => t.getId == d._1))
        .map { case (moduleDepId, scope) =>
          ModuleDep(TargetId(moduleId), TargetId(moduleDepId.getUri), scope, `export` = true)
        }
    }
  } yield d

  private[importing] def calculateSyntheticDependencies(moduleDependencies: Seq[ModuleDep], projectModules: ProjectModules) = {
    // 1. synthetic module is depended on by all its parent targets
    // 2. synthetic module depends on all parent target's dependencies
    val dependencyByParent = moduleDependencies.groupBy(_.parent)

    for {
      moduleDescription <- projectModules.synthetic
      synthParent <- moduleDescription.data.targets
      dep <- {
        val synthId = SynthId(moduleDescription.data.idUri)
        val parentId = TargetId(synthParent.getId.getUri)

        val parentDeps = dependencyByParent.getOrElse(parentId, Seq.empty)
        val inheritedDeps = parentDeps.map { d => ModuleDep(synthId, d.child, d.scope, `export` = false) }

        val parentSynthDependency = ModuleDep(parentId, synthId, DependencyScope.COMPILE, `export` = true)
        parentSynthDependency +: inheritedDeps
      }
    } yield dep
  }

  private[importing] def addModuleDependencies(dependencies: Seq[ModuleDep],
                                               idToModules: Map[DependencyId, DataNode[ModuleData]]): Seq[DataNode[ModuleData]] = {
    dependencies.flatMap { dep =>
      for {
        parent <- idToModules.get(dep.parent)
        child <- idToModules.get(dep.child)
      } yield {
        addDep(parent, child, dep.scope, dep.`export`)
      }
    }
  }

  private def addDep(parent: DataNode[ModuleData],
                     child: DataNode[ModuleData],
                     scope: DependencyScope,
                     exported: Boolean
                    ): DataNode[ModuleData] = {

    val data = new ModuleDependencyData(parent.getData, child.getData)
    data.setScope(scope)
    data.setExported(exported)

    val node = new DataNode[ModuleDependencyData](ProjectKeys.MODULE_DEPENDENCY, data, parent)
    parent.addChild(node)
    child
  }

  private[importing] def addNodeKindData(
    moduleNode: DataNode[ModuleData],
    moduleKind: ModuleKind,
    moduleDescriptionData: ModuleDescriptionData,
    buildTargetIdToBuildModuleTargetId: Map[MyURI, MyURI]
  ): Unit =
    moduleKind match {
      case ModuleKind.ScalaModule(_, scalaSdkData) =>

        val moduleData = moduleNode.getData

        // the "library" serves just as a marker in the case of BSP, as the compiler is not used. We leave it empty to avoid conflicts when working on Scala itself.
        val scalaSdkLibrary = new LibraryData(BSP.ProjectSystemId, s"${ScalaSdkData.LibraryName}-${scalaSdkData.scalaVersion}")
        val scalaSdkLibraryDependencyData = new LibraryDependencyData(moduleData, scalaSdkLibrary, LibraryLevel.MODULE)
        scalaSdkLibraryDependencyData.setScope(DependencyScope.COMPILE)

        val scalaSdkLibraryNode = new DataNode[LibraryDependencyData](ProjectKeys.LIBRARY_DEPENDENCY, scalaSdkLibraryDependencyData, moduleNode)
        moduleNode.addChild(scalaSdkLibraryNode)

        val scalaSdkNode = new DataNode[ScalaSdkData](ScalaSdkData.Key, scalaSdkData, moduleNode)
        moduleNode.addChild(scalaSdkNode)

        val buildModuleIdOpt = buildTargetIdToBuildModuleTargetId.get(new MyURI(moduleDescriptionData.idUri))
        buildModuleIdOpt match {
          case Some(buildModuleId) =>
            val moduleId = new MyURI(moduleDescriptionData.idUri)
            val sbtModuleData = SbtModuleDataBsp(moduleId, buildModuleId)
            val sbtModuleDataNode = new DataNode[SbtModuleDataBsp](SbtModuleDataBsp.Key, sbtModuleData, moduleNode)
            moduleNode.addChild(sbtModuleDataNode)
          case _ =>
        }

      case ModuleKind.SbtModule(_, scalaSdkData, sbtBuildModuleData) =>
        val scalaSdkNode = new DataNode[ScalaSdkData](ScalaSdkData.Key, scalaSdkData, moduleNode)
        val sbtBuildModuleDataNode = new DataNode[SbtBuildModuleDataBsp](SbtBuildModuleDataBsp.Key, sbtBuildModuleData, moduleNode)

        moduleNode.addChild(scalaSdkNode)
        moduleNode.addChild(sbtBuildModuleDataNode)

      case ModuleKind.JvmModule(JdkData(javaHome, javaVersion)) =>
      // FIXME set jdk from home or version

      case ModuleKind.UnspecifiedModule() =>
    }

  private[importing] sealed abstract class DependencyId(id: String)
  private[importing] case class SynthId(id: String) extends DependencyId(id)
  private[importing] case class TargetId(id: String) extends DependencyId(id)

  private[importing] case class ModuleDep(parent: DependencyId, child: DependencyId, scope: DependencyScope, `export`: Boolean)

  private[importing] case class Library(name: String, binary: File, sources: Option[File]) {
    val data: LibraryData = {
      val libraryData = new LibraryData(BSP.ProjectSystemId, name)
      libraryData.addPath(LibraryPathType.BINARY, binary.getCanonicalPathOptimized)
      sources.foreach(src => libraryData.addPath(LibraryPathType.BINARY, src.getCanonicalPathOptimized))
      libraryData
    }
  }

}
