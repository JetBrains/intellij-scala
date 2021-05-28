package org.jetbrains.plugins.scala
package project.maven

import com.intellij.build.events.BuildEvent
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.{Module, ModuleType, StdModuleTypes}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.PairConsumer
import org.jdom.Element
import org.jetbrains.idea.maven.importing.{MavenImporter, MavenRootModelAdapter}
import org.jetbrains.idea.maven.model.{MavenArtifact, MavenArtifactInfo, MavenPlugin}
import org.jetbrains.idea.maven.project._
import org.jetbrains.idea.maven.server.{MavenEmbedderWrapper, NativeMavenProjectHolder}
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.Importer
import org.jetbrains.plugins.scala.project.maven.ScalaMavenImporter._

import java.io.File
import java.util
import scala.jdk.CollectionConverters._

/**
 * @author Pavel Fatin
 */
class ScalaMavenImporter extends MavenImporter("org.scala-tools", "maven-scala-plugin") {

  override def getModuleType: ModuleType[_ <: ModuleBuilder] =  StdModuleTypes.JAVA

  override def collectSourceRoots(
    mavenProject: MavenProject,
    result: PairConsumer[String, JpsModuleSourceRootType[_]]
  ): Unit = {
    val sourceFolders = getSourceFolders(mavenProject)
    val testFolders = getTestFolders(mavenProject)

    sourceFolders.foreach(result.consume(_, JavaSourceRootType.SOURCE))
    testFolders.foreach(result.consume(_, JavaSourceRootType.TEST_SOURCE))
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

  // exclude "default" plugins, should be done inside IDEA's MavenImporter itself
  override def isApplicable(mavenProject: MavenProject): Boolean = validConfigurationIn(mavenProject).isDefined

  override def preProcess(
    module: Module,
    mavenProject: MavenProject,
    changes: MavenProjectChanges,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = {}

  // called after `resolve`
  override def process(
    modelsProvider: IdeModifiableModelsProvider,
    module: Module,
    rootModel: MavenRootModelAdapter,
    mavenModel: MavenProjectsTree,
    mavenProject: MavenProject,
    changes: MavenProjectChanges,
    mavenProjectToModuleName: util.Map[MavenProject, String],
    postTasks: util.List[MavenProjectsProcessorTask]
  ): Unit = {
    validConfigurationIn(mavenProject).foreach { configuration =>
      // TODO configuration.vmOptions

      val compilerOptions = {
        val plugins = configuration.plugins.map(id => mavenProject.localPathTo(id).getPath)
        configuration.compilerOptions ++ plugins.map(path => "-Xplugin:" + path)
      }

      module.configureScalaCompilerSettingsFrom("Maven", compilerOptions)

      val Some(version) = configuration.compilerVersion

      convertScalaLibraryToSdk(module, modelsProvider, version)
    }
  }

  private def convertScalaLibraryToSdk(
    module: Module,
    modelsProvider: IdeModifiableModelsProvider,
    compilerVersion: String,
  ): Unit = {
    val expectedLibraryName = scalaCompilerArtifactName("library", compilerVersion)
    val maybeScalaLibrary = modelsProvider.getAllLibraries.find { library =>
      val libraryName = library.getName
      libraryName.contains(expectedLibraryName) &&
        library.compilerVersion.contains(compilerVersion)
    }
    maybeScalaLibrary match {
      case Some(scalaLibrary) =>
        if (!scalaLibrary.isScalaSdk) {
          val compilerClasspathFull = module.getProject.getUserData(MavenFullCompilerClasspathKey)
          val properties = ScalaLibraryProperties(Some(compilerVersion), compilerClasspathFull)
          Importer.setScalaSdk(modelsProvider, scalaLibrary, properties)
        }
      case None =>
        val msg = s"Cannot find project Scala library $compilerVersion for module ${module.getName}"
        val exception = new IllegalArgumentException(msg)
        val console = MavenProjectsManager.getInstance(module.getProject).getSyncConsole
        console.addException(exception, (_: Any, _: BuildEvent) => ())
    }
  }

  // called before `process`
  override def resolve(
    project: Project,
    mavenProject: MavenProject,
    nativeMavenProject: NativeMavenProjectHolder,
    embedder: MavenEmbedderWrapper,
    context: ResolveContext
  ): Unit = {
    val configuration = validConfigurationIn(mavenProject)
    configuration.foreach { configuration =>
      val repositories = mavenProject.getRemoteRepositories

      def pom(id: MavenId): MavenArtifactInfo = new MavenArtifactInfo(id.groupId, id.artifactId, id.version, "pom", null)
      def jar(id: MavenId): MavenArtifactInfo = new MavenArtifactInfo(id.groupId, id.artifactId, id.version, "jar", id.classifier.orNull)

      def resolve(id: MavenId): MavenArtifact = {
        embedder.resolve(pom(id), repositories)
        embedder.resolve(jar(id), repositories)
      }

      def resolveTransitively(id: MavenId): Seq[MavenArtifact] = {
        // NOTE: we can't use Seq + `asScala` here because the list will be serialised and passed to an external process
        val artifacts = util.Arrays.asList(jar(id))
        embedder.resolveTransitively(artifacts, repositories).asScala.toSeq
      }

      // Scala Maven plugin can add scala-library to compilation classpath, without listing it as a project dependency.
      // Such an approach is probably incorrect, but we have to support that behaviour "as is".
      configuration.implicitScalaLibrary.map(resolve).foreach(mavenProject.addDependency)
      // compiler classpath should be resolved transitively, e.g. Scala3 compiler contains quite a lot of jar files in the classpath
      val compilerClasspathWithTransitives: Seq[File] = resolveTransitively(configuration.compilerArtifact).map(_.getFile)
      project.putUserData(MavenFullCompilerClasspathKey, compilerClasspathWithTransitives)

      configuration.plugins.foreach(resolve)
    }
  }

  private def validConfigurationIn(project: MavenProject) = Some(new ScalaConfiguration(project)).filter(_.valid)
}

private object ScalaMavenImporter {

  /**
   * Hack Key to keep info about full compiler classpath after it's resolved in [[ScalaMavenImporter.resolve]]
   * to use it later in [[ScalaMavenImporter.process]] when creating Scala SDK
   *
   * !!! NOTE: we assume that Maven project can have only single Scala SDK when using scala-maven-plugin
   */
  private val MavenFullCompilerClasspathKey = Key.create[Seq[File]]("MavenFullCompilerClasspathKey")

  implicit class RichMavenProject(private val project: MavenProject) extends AnyVal {
    def localPathTo(id: MavenId): File = {
      val suffix = id.classifier.map("-" + _).getOrElse("")
      val jarName = s"${id.artifactId}-${id.version}$suffix.jar"
      project.getLocalRepository / id.groupId.replaceAll("\\.", "/") /
        id.artifactId / id.version / jarName
    }
  }

  implicit class RichFile(private val file: File) extends AnyVal {
    def /(child: String): File = new File(file, child)
  }

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

    private def standardLibrary: Option[MavenArtifact] = {
      // Scala3 should go first (Scala3 also includes Scala2 library)
      val maybeScala3 = project.findDependencies("org.scala-lang", "scala3-library_3").asScala.headOption
      val result = maybeScala3.orElse(project.findDependencies("org.scala-lang", "scala-library").asScala.headOption)
      result
    }

    // An implied scala-library dependency when there's no explicit scala-library dependency, but scalaVersion is given.
    def implicitScalaLibrary: Option[MavenId] = Some((compilerVersionProperty, standardLibrary)) collect  {
      case (Some(compilerVersion), None) => scalaCompilerArtifactId("library", compilerVersion)
    }

    def compilerArtifact: MavenId = {
      val version = versionNumber
      scalaCompilerArtifactId("compiler", version)
    }

    private def versionNumber = compilerVersion.getOrElse("unknown")

    def compilerVersion: Option[String] = compilerVersionProperty
      .orElse(standardLibrary.map(_.getVersion))

    private def compilerVersionProperty: Option[String] = resolvePluginConfig(configElementName = "scalaVersion", userPropertyName = "scala.version")

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
    MavenId("org.scala-lang", artifactName, scalaVersion)
  }

  private def scalaCompilerArtifactName(artifactSuffix: String, scalaVersion: String): String =
    if (scalaVersion.startsWith("3."))
      s"scala3-${artifactSuffix}_3"
    else
      s"scala-$artifactSuffix"
}
