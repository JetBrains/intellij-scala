package org.jetbrains.plugins.scala
package project.maven

import java.io.File
import java.util

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryEx.ModifiableModelEx
import org.jdom.Element
import org.jetbrains.idea.maven.importing.{MavenImporter, MavenRootModelAdapter}
import org.jetbrains.idea.maven.model.{MavenArtifact, MavenArtifactInfo, MavenId, MavenPlugin}
import org.jetbrains.idea.maven.project._
import org.jetbrains.idea.maven.server.{MavenEmbedderWrapper, NativeMavenProjectHolder}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.maven.ScalaMavenImporter._
import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class ScalaMavenImporter extends MavenImporter("org.scala-tools", "maven-scala-plugin") {
  override def collectSourceFolders(mavenProject: MavenProject, result: java.util.List[String]) {
    collectSourceOrTestFolders(mavenProject, "add-source", "sourceDir", "src/main/scala", result)
  }

  override def collectTestFolders(mavenProject: MavenProject, result: java.util.List[String]) {
    collectSourceOrTestFolders(mavenProject, "add-source", "testSourceDir", "src/test/scala", result)
  }

  private def collectSourceOrTestFolders(mavenProject: MavenProject, goal: String, goalPath: String,
                                         defaultDir: String, result: java.util.List[String]) {
    val goalConfigValue = findGoalConfigValue(mavenProject, goal, goalPath)
    result.add(if (goalConfigValue == null) defaultDir else goalConfigValue)
  }

  // exclude "default" plugins, should be done inside IDEA's MavenImporter itself
  override def isApplicable(mavenProject: MavenProject): Boolean = validConfigurationIn(mavenProject).isDefined

  override def preProcess(module: Module, mavenProject: MavenProject, changes: MavenProjectChanges, modelsProvider: IdeModifiableModelsProvider) {}

  override def process(modelsProvider: IdeModifiableModelsProvider, module: Module,
              rootModel: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject,
              changes: MavenProjectChanges, mavenProjectToModuleName: util.Map[MavenProject, String],
              postTasks: util.List[MavenProjectsProcessorTask]) {

    validConfigurationIn(mavenProject).foreach { configuration =>
      // TODO configuration.vmOptions

      val compilerOptions = {
        val plugins = configuration.plugins.map(id => mavenProject.localPathTo(id).getPath)
        configuration.compilerOptions ++ plugins.map(path => "-Xplugin:" + path)
      }
      
      module.configureScalaCompilerSettingsFrom("Maven", compilerOptions)

      val compilerVersion = configuration.compilerVersion.get

      val scalaLibrary =modelsProvider.getAllLibraries.toSeq
              .filter(_.getName.contains("scala-library"))
              .find(_.scalaVersion.contains(compilerVersion))
              .getOrElse(throw new ExternalSystemException("Cannot find project Scala library " +
              compilerVersion.presentation + " for module " + module.getName))

      if (!scalaLibrary.isScalaSdk) {
        val languageLevel = compilerVersion.toLanguageLevel.getOrElse(ScalaLanguageLevel.Default)
        val compilerClasspath = configuration.compilerClasspath.map(mavenProject.localPathTo)

        val libraryModel = modelsProvider.getModifiableLibraryModel(scalaLibrary).asInstanceOf[LibraryEx.ModifiableModelEx]
        convertToScalaSdk(libraryModel, languageLevel, compilerClasspath)
      }
    }
  }

  // Can we reuse library.convertToScalaSdk? (dependency on modifiable model, cannot commit model)
  private def convertToScalaSdk(model: ModifiableModelEx, languageLevel: ScalaLanguageLevel, compilerClasspath: Seq[File]) {
    model.setKind(ScalaLibraryKind)

    val properties = new ScalaLibraryProperties()
    properties.languageLevel = languageLevel
    properties.compilerClasspath = compilerClasspath

    model.setProperties(properties)
  }

  override def resolve(project: Project, mavenProject: MavenProject, nativeMavenProject: NativeMavenProjectHolder,
                       embedder: MavenEmbedderWrapper) {
    validConfigurationIn(mavenProject).foreach { configuration =>
      val repositories = mavenProject.getRemoteRepositories

      def resolve(id: MavenId): MavenArtifact = {
        embedder.resolve(new MavenArtifactInfo(id, "pom", null), repositories)
        embedder.resolve(new MavenArtifactInfo(id, "jar", null), repositories)
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
  implicit class RichMavenProject(val project: MavenProject) extends AnyVal {
    def localPathTo(id: MavenId): File = project.getLocalRepository / id.getGroupId.replaceAll("\\.", "/") /
            id.getArtifactId / id.getVersion / "%s-%s.jar".format(id.getArtifactId, id.getVersion)
  }

  implicit class RichFile(val file: File) extends AnyVal {
    def /(child: String): File = new File(file, child)
  }

  private class ScalaConfiguration(project: MavenProject) {
    private def versionNumber = compilerVersion.map(_.presentation).getOrElse("unknown")

    private def scalaCompilerId = new MavenId("org.scala-lang", "scala-compiler", versionNumber)

    private def scalaLibraryId = new MavenId("org.scala-lang", "scala-library", versionNumber)

    private def scalaReflectId = new MavenId("org.scala-lang", "scala-reflect", versionNumber)

    private def compilerPlugin: Option[MavenPlugin] =
      project.findPlugin("org.scala-tools", "maven-scala-plugin").toOption.filter(!_.isDefault).orElse(
        project.findPlugin("net.alchim31.maven", "scala-maven-plugin").toOption.filter(!_.isDefault)).orElse(
        project.findPlugin("com.triplequote.maven", "scala-maven-plugin").toOption.filter(!_.isDefault))

    private def compilerConfigurations: Seq[Element] = compilerPlugin.toSeq.flatMap { plugin =>
      plugin.getConfigurationElement.toOption.toSeq ++
        plugin.getGoalConfiguration("compile").toOption.toSeq
    }

    private def standardLibrary: Option[MavenArtifact] =
      project.findDependencies("org.scala-lang", "scala-library").asScala.headOption

    // An implied scala-library dependency when there's no explicit scala-library dependency, but scalaVersion is given.
    def implicitScalaLibrary: Option[MavenId] = Some(compilerVersionProperty, standardLibrary) collect  {
      case (Some(compilerVersion), None) => new MavenId("org.scala-lang", "scala-library", compilerVersion)
    }

    def compilerClasspath: Seq[MavenId] = Seq(scalaCompilerId, scalaLibraryId, scalaReflectId)

    def compilerVersion: Option[Version] = compilerVersionProperty
      .orElse(standardLibrary.map(_.getVersion)).map(Version(_))

    private def compilerVersionProperty: Option[String] = element("scalaVersion").map(_.getTextTrim)

    def vmOptions: Seq[String] = elements("jvmArgs", "jvmArg").map(_.getTextTrim)

    def compilerOptions: Seq[String] = elements("args", "arg").map(_.getTextTrim)

    def plugins: Seq[MavenId] = {
      elements("compilerPlugins", "compilerPlugin").flatMap { plugin =>
        plugin.getChildTextTrim("groupId").toOption
          .zip(plugin.getChildTextTrim("artifactId").toOption)
          .zip(plugin.getChildTextTrim("version").toOption).map {
          case ((groupId, artifactId), version) => new MavenId(groupId, artifactId, version)
        }
      }
    }

    private def elements(root: String, name: String): Seq[Element] =
      element(root).toSeq.flatMap(elements(_, name))

    private def elements(root: Element, name: String): Seq[Element] =
      root.getChildren(name).asScala

    private def element(name: String): Option[Element] =
      compilerConfigurations.flatMap(_.getChild(name).toOption.toSeq).headOption

    def valid: Boolean = compilerPlugin.isDefined && compilerVersion.isDefined
  }
}
