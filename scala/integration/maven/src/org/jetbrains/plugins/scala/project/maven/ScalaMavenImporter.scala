package org.jetbrains.plugins.scala
package project.maven

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.jetbrains.idea.maven.importing.{MavenImporter, MavenRootModelAdapter}
import org.jetbrains.idea.maven.model.{MavenArtifact, MavenArtifactInfo, MavenPlugin}
import org.jetbrains.idea.maven.project._
import org.jetbrains.idea.maven.server.{MavenEmbedderWrapper, NativeMavenProjectHolder}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.external.Importer
import org.jetbrains.plugins.scala.project.maven.ScalaMavenImporter._

import scala.jdk.CollectionConverters._

/**
 * @author Pavel Fatin
 */
class ScalaMavenImporter extends MavenImporter("org.scala-tools", "maven-scala-plugin") {
  override def collectSourceFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit = {
    collectSourceOrTestFolders(mavenProject, "add-source", "sourceDir", "src/main/scala", result)
  }

  override def collectTestFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit = {
    collectSourceOrTestFolders(mavenProject, "add-source", "testSourceDir", "src/test/scala", result)
  }

  private def collectSourceOrTestFolders(mavenProject: MavenProject, goal: String, goalPath: String,
                                         defaultDir: String, result: java.util.List[String]): Unit = {
    val goalConfigValue = findGoalConfigValue(mavenProject, goal, goalPath)
    result.add(if (goalConfigValue == null) defaultDir else goalConfigValue)
  }

  // exclude "default" plugins, should be done inside IDEA's MavenImporter itself
  override def isApplicable(mavenProject: MavenProject): Boolean = validConfigurationIn(mavenProject).isDefined

  override def preProcess(module: Module, mavenProject: MavenProject, changes: MavenProjectChanges, modelsProvider: IdeModifiableModelsProvider): Unit = {}

  override def process(modelsProvider: IdeModifiableModelsProvider, module: Module,
              rootModel: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject,
              changes: MavenProjectChanges, mavenProjectToModuleName: util.Map[MavenProject, String],
              postTasks: util.List[MavenProjectsProcessorTask]): Unit = {

    validConfigurationIn(mavenProject).foreach { configuration =>
      // TODO configuration.vmOptions

      val compilerOptions = {
        val plugins = configuration.plugins.map(id => mavenProject.localPathTo(id).getPath)
        configuration.compilerOptions ++ plugins.map(path => "-Xplugin:" + path)
      }

      module.configureScalaCompilerSettingsFrom("Maven", compilerOptions)

      val Some(version) = configuration.compilerVersion

      val scalaLibrary = modelsProvider.getAllLibraries
        .find { library =>
          library.getName.contains("scala-library") &&
            library.compilerVersion.contains(version)
        }.getOrElse {
        throw new ExternalSystemException(s"Cannot find project Scala library $version for module ${module.getName}")
      }

      if (!scalaLibrary.isScalaSdk) {
        Importer.setScalaSdk(
          modelsProvider,
          scalaLibrary,
          ScalaLibraryProperties(Some(version), configuration.compilerClasspath.map(mavenProject.localPathTo)
          )
        )
      }
    }
  }

  override def resolve(project: Project, mavenProject: MavenProject, nativeMavenProject: NativeMavenProjectHolder,
                       embedder: MavenEmbedderWrapper): Unit = {
    validConfigurationIn(mavenProject).foreach { configuration =>
      val repositories = mavenProject.getRemoteRepositories

      def resolve(id: MavenId): MavenArtifact = {
        embedder.resolve(new MavenArtifactInfo(id.groupId, id.artifactId, id.version, "pom", null), repositories)
        embedder.resolve(new MavenArtifactInfo(id.groupId, id.artifactId, id.version, "jar", id.classifier.orNull), repositories)
      }

      // Scala Maven plugin can add scala-library to compilation classpath, without listing it as a project dependency.
      // Such an approach is probably incorrect, but we have to support that behaviour "as is".
      configuration.implicitScalaLibrary.foreach { scalaLibraryId =>
        mavenProject.addDependency(resolve(scalaLibraryId))
      }
      configuration.compilerClasspath.foreach(resolve)
      configuration.plugins.foreach(resolve)
    }
  }

  private def validConfigurationIn(project: MavenProject) = Some(new ScalaConfiguration(project)).filter(_.valid)
}

private object ScalaMavenImporter {
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
    private def versionNumber = compilerVersion.getOrElse("unknown")

    private def scalaCompilerId = MavenId("org.scala-lang", "scala-compiler", versionNumber)

    private def scalaLibraryId = MavenId("org.scala-lang", "scala-library", versionNumber)

    private def scalaReflectId = MavenId("org.scala-lang", "scala-reflect", versionNumber)

    private def compilerPlugin: Option[MavenPlugin] =
      project.findPlugin("org.scala-tools", "maven-scala-plugin").toOption.filter(!_.isDefault).orElse(
        project.findPlugin("net.alchim31.maven", "scala-maven-plugin").toOption.filter(!_.isDefault)).orElse(
        project.findPlugin("com.triplequote.maven", "scala-maven-plugin").toOption.filter(!_.isDefault)).orElse(
        project.findPlugin("com.google.code.sbt-compiler-maven-plugin", "sbt-compiler-maven-plugin").toOption.filter(!_.isDefault))

    private def compilerConfigurations: Seq[Element] = compilerPlugin.toSeq.flatMap { plugin =>
      plugin.getConfigurationElement.toOption.toSeq ++
        plugin.getGoalConfiguration("compile").toOption.toSeq
    }

    private def standardLibrary: Option[MavenArtifact] =
      project.findDependencies("org.scala-lang", "scala-library").asScala.headOption

    // An implied scala-library dependency when there's no explicit scala-library dependency, but scalaVersion is given.
    def implicitScalaLibrary: Option[MavenId] = Some(compilerVersionProperty, standardLibrary) collect  {
      case (Some(compilerVersion), None) => MavenId("org.scala-lang", "scala-library", compilerVersion)
    }

    def compilerClasspath: Seq[MavenId] = Seq(scalaCompilerId, scalaLibraryId, scalaReflectId)

    def compilerVersion: Option[String] = compilerVersionProperty
      .orElse(standardLibrary.map(_.getVersion))

    private def compilerVersionProperty: Option[String] = resolvePluginConfig(configElementName = "scalaVersion", userPropertyName = "scala.version")

    def vmOptions: Seq[String] = elements("jvmArgs", "jvmArg").map(_.getTextTrim)

    def compilerOptions: Seq[String] = {
      val args: Seq[String] = elements("args", "arg").map(_.getTextTrim)
      val addScalacArgs: Option[String] = resolvePluginConfig(configElementName = "addScalacArgs", userPropertyName = "addScalacArgs")
      val addScalacArgsSplit: Seq[String] = addScalacArgs.toSeq.flatMap(_.split("\\|"))

      // NB scala-maven-plugin puts addScalacArgs after args
      args ++ addScalacArgsSplit
    }

    def plugins: Seq[MavenId] = {
      elements("compilerPlugins", "compilerPlugin").flatMap { plugin =>
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
    }

    private def elements(root: String, name: String): Seq[Element] =
      element(root).toSeq.flatMap(elements(_, name))

    private def elements(root: Element, name: String): collection.Seq[Element] =
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
}
