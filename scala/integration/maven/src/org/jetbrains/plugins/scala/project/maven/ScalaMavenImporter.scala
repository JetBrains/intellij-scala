package org.jetbrains.plugins.scala.project.maven

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.JavaCoroutines
import kotlin.coroutines.Continuation
import org.jdom.Element
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator.{AdditionalFolder, FolderType, FoldersContext, MutableMavenProjectContext}
import org.jetbrains.idea.maven.importing.{MavenApplicableConfigurator, MavenWorkspaceConfigurator}
import org.jetbrains.idea.maven.model.{MavenArtifact, MavenArtifactInfo, MavenPlugin}
import org.jetbrains.idea.maven.project._
import org.jetbrains.idea.maven.server.{MavenArtifactResolutionRequest, MavenEmbedderWrapper}
import org.jetbrains.idea.maven.utils.MavenJDOMUtil
import org.jetbrains.jps.model.serialization.SerializationConstants
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.ScalaSdkUtils
import org.jetbrains.plugins.scala.project.maven.ScalaMavenImporter._

import java.nio.file.Path
import java.util
import java.util.stream.Stream
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.util.Try

final class ScalaMavenImporter extends MavenApplicableConfigurator("org.scala-tools", "maven-scala-plugin")
  with MavenProjectResolutionContributor with MavenWorkspaceConfigurator {

  override def getAdditionalFolders(
    context: FoldersContext
  ): Stream[AdditionalFolder] = {
    def createAdditionalFolders(folder: String, folderType: FolderType): AdditionalFolder =
      new AdditionalFolder(folder, folderType)

    val scalaConfiguration = validConfigurationIn(context.getMavenProject)
    val folders = scalaConfiguration match {
      case Some(_) =>
        val sourceFolders = getSourceFolders(context.getMavenProject).map(createAdditionalFolders(_, FolderType.SOURCE))
        val testFolders = getTestFolders(context.getMavenProject).map(createAdditionalFolders(_, FolderType.TEST_SOURCE))
        sourceFolders ++ testFolders
      case _ => Seq.empty
    }
    folders.asJava.stream()
  }

  private def getSourceFolders(mavenProject: MavenProject): Seq[String] =
    getSourceOrTestFolders(mavenProject, "add-source", "sourceDir", "src/main/scala")

  private def getTestFolders(mavenProject: MavenProject): Seq[String] =
    getSourceOrTestFolders(mavenProject, "add-source", "testSourceDir", "src/test/scala")

  private def getSourceOrTestFolders(
    mavenProject: MavenProject,
    goal: String,
    goalPath: String,
    defaultDir: String,
  ): Seq[String] = {
    val goalConfigValue = findGoalConfigValue(mavenProject, goal, goalPath)
    val result = if (goalConfigValue == null) defaultDir else goalConfigValue
    Seq(result)
  }

  //TODO remove it, when it's implemented on the platform side
  private def findGoalConfigValue(mavenProject: MavenProject, goal: String, goalPath: String): String = {
    val goalConfig = mavenProject.getPluginGoalConfiguration("org.scala-tools", "maven-scala-plugin", goal)
    MavenJDOMUtil.findChildValueByPath(goalConfig, goalPath)
  }

  // exclude "default" plugins, should be done inside IDEA's MavenImporter itself
  override def isApplicable(mavenProject: MavenProject): Boolean = validConfigurationIn(mavenProject).isDefined

  override def configureMavenProject(context: MutableMavenProjectContext): Unit = {
    val mavenProjectWithModules = context.getMavenProjectWithModules

    val mavenProject = mavenProjectWithModules.getMavenProject
    validConfigurationIn(mavenProject).foreach { configuration =>
      // TODO configuration.vmOptions
      val compilerOptions = {
        val plugins = configuration.plugins.map(id => mavenProject.localPathTo(id))
        configuration.compilerOptions ++ plugins.map(path => "-Xplugin:" + path.toString)
      }

      val compileOrder = configuration.compileOrder.getOrElse(CompileOrder.Mixed)
      val project = context.getProject
      val moduleEntityTypes = mavenProjectWithModules.getModules.asScala
      moduleEntityTypes.foreach { moduleEntityType =>
        val moduleEntity = moduleEntityType.getModule
        moduleEntity.configureScalaCompilerSettingsFrom(project, "Maven", compilerOptions, compileOrder)

        configuration.compilerVersion match {
          case Some(compilerVersion) =>
            configureScalaSdk(moduleEntity, context.getStorage, project, compilerVersion, mavenProject)
          case _ =>
        }
      }
    }
  }

  /**
   * There might be the case when scala library minor version is different from the minor version
   * of the scala-compiler.
   * This is so when there is no any explicit scala-library dependency in maven project.<br>
   * In this case running `main` in maven (via mvn package exec:java -Dexec.mainClass=Main)
   * will use the version from the explicit dependencies, not the version from the compiler.
   *
   * see [[implicitScalaLibraryIfNeeded]]
   */
  private def configureScalaSdk(
    module: ModuleEntity,
    storage: MutableEntityStorage,
    project: Project,
    compilerVersion: String,
    mavenProject: MavenProject
  ): Unit = {
    val compilerClasspathFull = mavenProject.getCachedValue(MavenFullCompilerClasspathKey)

    val compilerBridgeBinaryJar = ScalaSdkUtils.compilerBridgeJarName(compilerVersion).flatMap { bridgeJarName =>
      compilerClasspathFull.find(_.getFileName.toString == bridgeJarName)
    }

    val classpath = compilerClasspathFull.diff(compilerBridgeBinaryJar.toSeq)

    ScalaSdkUtils.configureScalaSdk(
      module,
      compilerVersion,
      classpath.map(_.toFile),
      scaladocExtraClasspath = Nil,
      compilerBridgeBinaryJar.map(_.toFile),
      sdkPrefix = "Maven",
      storage,
      project,
      scalaSdkSourceId = SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
    )
  }

  // called before `configureMavenProject`
  def resolve(mavenProject: MavenProject, embedder: MavenEmbedderWrapper): Unit = {
    val configuration = validConfigurationIn(mavenProject)
    configuration.foreach { configuration =>
      val repositories = mavenProject.getRemoteRepositories

      def pom(id: MavenId): MavenArtifactInfo = new MavenArtifactInfo(id.groupId, id.artifactId, id.version, "pom", null)
      def jar(id: MavenId): MavenArtifactInfo = new MavenArtifactInfo(id.groupId, id.artifactId, id.version, "jar", id.classifier.orNull)

      def resolveArtifact(info: MavenArtifactInfo): util.List[MavenArtifact] = {
        val request = new MavenArtifactResolutionRequest(info, repositories)
        embedder.resolveArtifacts(util.List.of(request), null, null, null): @nowarn("cat=deprecation") // deprecated to be replaced with a suspend fun
      }

      def resolveJar(id: MavenId): MavenArtifact = {
        //note sure why, but resolving of poms is done before resolving jars, it was done so since this class was created
        resolveArtifact(pom(id))
        resolveArtifact(jar(id)).get(0)
      }

      def resolveTransitively(id: MavenId): Seq[MavenArtifact] = {
        // NOTE: we can't use Seq + `asScala` here because the list will be serialised and passed to an external process
        val artifacts = util.Arrays.asList(jar(id))
        val artifactResolveResult = embedder.resolveArtifactTransitively(artifacts, repositories): @nowarn("cat=deprecation") // TODO: deprecated to be replaced with a suspend fun. See IDEA-340501
        val resolved = artifactResolveResult.mavenResolvedArtifacts.asScala.toSeq
        // TODO: ideally test scope dependencies shouldn't be downloaded at all (see IDEA-270126)
        // note, resolved also includes root compiler jar with a `null` scope
        resolved.filter(_.getScope != "test")
      }

      // Scala Maven plugin can add scala-library to compilation classpath, without listing it as a project dependency.
      // Such an approach is probably incorrect, but we have to support that behaviour "as is".
      // TODO check if it's still true (it was created in 2017)
      val implicitScalaLibrary = implicitScalaLibraryIfNeeded(configuration)
      implicitScalaLibrary.map(resolveJar).foreach(mavenProject.addDependency): @nowarn("cat=deprecation") //TODO SCL-23050

      val compilerBridgeJar = configuration.compilerBridgeArtifact.map(resolveJar).map(a => Path.of(a.getPath))

      // compiler classpath should be resolved transitively, e.g. Scala3 compiler contains quite a lot of jar files in the classpath
      val compilerClasspathWithTransitives: Seq[Path] =
        resolveTransitively(configuration.compilerArtifact).map(a => Path.of(a.getPath)) ++ compilerBridgeJar

      mavenProject.putCachedValue(MavenFullCompilerClasspathKey, compilerClasspathWithTransitives)

      configuration.plugins.foreach(resolveJar)
    }
  }

  // See IDEA-340501
  override def onMavenProjectResolved(
    project: Project,
    mavenProject: MavenProject,
    mavenEmbedderWrapper: MavenEmbedderWrapper,
    continuation: Continuation[_ >: kotlin.Unit]
  ): AnyRef = JavaCoroutines.suspendJava[kotlin.Unit](
    javaContinuation => {
      resolve(mavenProject, mavenEmbedderWrapper)
      javaContinuation.resume(kotlin.Unit.INSTANCE)
    },
    continuation
  )

  /**
   * An implied scala-library dependency when there's no explicit scala-library dependency, but scalaVersion is given.<br>
   * Note: project can have transitive dependency on scala-library with non-"Compile" scope
   * (e.g. in Tests via dependency on some test framework).
   * We add implicit scala library only if there is no any Compile-time scala-library dependency.
   */
  private def implicitScalaLibraryIfNeeded(configuration: ScalaConfiguration): Option[MavenId] = {
    val scalaCompilerVersion = configuration.compilerVersionProperty
    scalaCompilerVersion.flatMap { compilerVersion =>
      val needNewLibrary = configuration.findScalaLibraryDependency match {
        case Some(existingLibraryDep) =>
          // note: it may lead to some issues, because if the scala version property is defined, then
          // the scala library should be available at least for compilation
          // (both for production and test sources, so the scala library should have a "compile" scope)

          // I've checked the default scala-maven-plugin behaviour when there is no scala library in the dependencies
          // (even transitive) but <scala.version> is present. In such a case the code can be compiled but cannot be run.
          // But if there is any scala library in the dependencies with no matter what scope then the code also be run
          // (e.g. if the scala library has Test scope, we can run a class in production sources).
          // TODO create a more accurate solution to this problem
          Option(existingLibraryDep.getScope).exists(_.toLowerCase != "compile")
        case _ =>
          true
      }
      if (needNewLibrary)
        Some(scalaCompilerArtifactId("library", compilerVersion))
      else
        None
    }
  }

  private def validConfigurationIn(project: MavenProject) = Some(new ScalaConfiguration(project)).filter(_.valid)
}

private object ScalaMavenImporter {

  /**
   * Hack Key to keep info about full compiler classpath after it's resolved in [[ScalaMavenImporter.resolve]]
   * to use it later in [[ScalaMavenImporter.process]] when creating Scala SDK
   *
   * This key is used for each Maven project/module (not the IntelliJ IDEA project),
   * so in a multi-module project, each module can have a different compiler classpath.
   */
  private val MavenFullCompilerClasspathKey = Key.create[Seq[Path]]("MavenFullCompilerClasspathKey")

  private final val OrgScalaLang = "org.scala-lang"

  implicit class RichMavenProject(private val project: MavenProject) extends AnyVal {
    def localPathTo(id: MavenId): Path = {
      val suffix = id.classifier.map("-" + _).getOrElse("")
      val jarName = s"${id.artifactId}-${id.version}$suffix.jar"
      project.getLocalRepositoryPath
        .resolve(id.groupId.replaceAll("\\.", "/"))
        .resolve(id.artifactId)
        .resolve(id.version)
        .resolve(jarName)
    }
  }

  //rename to `private val mavenProject`
  private class ScalaConfiguration(project: MavenProject) {

    private def compilerPlugin: Option[MavenPlugin] =
      project.findPlugin("org.scala-tools", "maven-scala-plugin").toOption.filter(!_.isDefault).orElse(
        project.findPlugin("net.alchim31.maven", "scala-maven-plugin").toOption.filter(!_.isDefault)).orElse(
        project.findPlugin("com.triplequote.maven", "scala-maven-plugin").toOption.filter(!_.isDefault)).orElse(
        project.findPlugin("com.google.code.sbt-compiler-maven-plugin", "sbt-compiler-maven-plugin").toOption.filter(!_.isDefault))

    private def compilerConfigurations: Seq[Element] = compilerPlugin.toSeq.flatMap { plugin =>
      plugin.getConfigurationElement.toOption.toSeq ++
        plugin.getGoalConfiguration("compile").toOption.toSeq
    }

    def findScalaLibraryDependency: Option[MavenArtifact] = {
      // Scala3 should go first (Scala3 also includes Scala2 library)
      val maybeScala3 = project.findDependencies(OrgScalaLang, "scala3-library_3").asScala.headOption
      val result = maybeScala3.orElse(project.findDependencies(OrgScalaLang, "scala-library").asScala.headOption)
      result
    }

    def compilerArtifact: MavenId = {
      val version = versionNumber
      scalaCompilerArtifactId("compiler", version)
    }

    def compilerBridgeArtifact: Option[MavenId] = for {
      version <- compilerVersion
      bridge <- ScalaSdkUtils.compilerBridgeName(version)
    } yield MavenId(OrgScalaLang, bridge, version)

    private def versionNumber = compilerVersion.getOrElse("unknown")

    def compilerVersion: Option[String] = compilerVersionProperty
      .orElse(findScalaLibraryDependency.map(_.getVersion))

    def compilerVersionProperty: Option[String] =
      resolvePluginConfig(configElementName = "scalaVersion", userPropertyName = "scala.version")

    def compileOrder: Option[CompileOrder] =
      resolvePluginConfig("compileOrder", "compileOrder")
        .flatMap(s => Try(CompileOrder.valueOf(s)).toOption)

    def compilerOptions: Seq[String] = {
      val args = elements("args", "arg").map(_.getTextTrim)
      val addScalacArgs = resolvePluginConfig(configElementName = "addScalacArgs", userPropertyName = "addScalacArgs")
      val addScalacArgsSplit = addScalacArgs.toSeq.flatMap(_.split("\\|"))

      // NB scala-maven-plugin puts addScalacArgs after args
      (args ++ addScalacArgsSplit).toSeq
    }

    def plugins: Seq[MavenId] = {
      elements("compilerPlugins", "compilerPlugin").iterator
        .flatMap { plugin =>
          plugin.getChildTextTrim("groupId").toOption
            .zip(plugin.getChildTextTrim("artifactId").toOption)
            .zip(plugin.getChildTextTrim("version").toOption)
          .map {
            case ((groupId, artifactId), version) =>
              // It's okay if classifier is absent or blank
              val classifier = plugin.getChildTextTrim("classifier").toOption
                .filterNot(_.isEmpty)
              MavenId(groupId, artifactId, version, classifier)
          }
        }
        .toSeq
    }

    private def elements(root: String, name: String): Iterator[Element] =
      element(root).iterator.flatMap(elements(_, name))

    private def elements(root: Element, name: String): Iterable[Element] =
      root.getChildren(name).asScala

    private def element(name: String): Option[Element] =
      compilerConfigurations.flatMap(_.getChild(name).toOption.toSeq).headOption

    // looks up a plugin parameter, via the plugin configuration if set directly, otherwise via its user property
    private def resolvePluginConfig(configElementName: String, userPropertyName: String): Option[String] =
      element(configElementName).map(_.getTextTrim)
        .orElse(Option(project.getProperties.getProperty(userPropertyName)).map(_.trim))
        .filter(_.nonEmpty)

    def valid: Boolean = compilerPlugin.isDefined && compilerVersion.isDefined
  }

  /**
    * Represents a Maven artifact by group, artifact, version and optional classifier.
    * Similar to [[org.jetbrains.idea.maven.model.MavenId]], but supports classifier.
    */
  private case class MavenId(groupId: String, artifactId: String, version: String, classifier: Option[String] = None)

  private def scalaCompilerArtifactId(artifactSuffix: String, scalaVersion: String): MavenId = {
    val artifactName = scalaCompilerArtifactName(artifactSuffix, scalaVersion)
    MavenId(OrgScalaLang, artifactName, scalaVersion)
  }

  private def scalaCompilerArtifactName(artifactSuffix: String, scalaVersion: String): String =
    if (scalaVersion.startsWith("3."))
      s"scala3-${artifactSuffix}_3"
    else
      s"scala-$artifactSuffix"
}
