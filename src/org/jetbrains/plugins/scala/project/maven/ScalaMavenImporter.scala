package org.jetbrains.plugins.scala
package project.maven

import org.jetbrains.idea.maven.importing.{MavenImporter, MavenModifiableModelsProvider, MavenRootModelAdapter}
import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.project._
import org.jdom.Element
import org.jetbrains.idea.maven.model.{MavenArtifact, MavenPlugin, MavenArtifactInfo, MavenId}
import org.jetbrains.idea.maven.server.{NativeMavenProjectHolder, MavenEmbedderWrapper}
import org.jetbrains.plugins.scala.extensions._
import com.intellij.openapi.project.Project
import java.io.File
import java.util
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10

import collection.JavaConversions._
import project._
import ScalaMavenImporter._
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryEx.ModifiableModelEx

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
  override def isApplicable(mavenProject: MavenProject) = validConfigurationIn(mavenProject).isDefined

  def preProcess(module: Module, mavenProject: MavenProject, changes: MavenProjectChanges,
                 modifiableModelsProvider: MavenModifiableModelsProvider) {}

  def process(modelsProvider: MavenModifiableModelsProvider, module: Module,
              rootModel: MavenRootModelAdapter, mavenModel: MavenProjectsTree, mavenProject: MavenProject,
              changes: MavenProjectChanges, mavenProjectToModuleName: util.Map[MavenProject, String], 
              postTasks: util.List[MavenProjectsProcessorTask]) {

    validConfigurationIn(mavenProject).foreach { configuration =>
      // TODO configuration.vmOptions

      val compilerOptions = {
        val plugins = configuration.plugins.map(id => mavenProject.localPathTo(id).getPath)
        configuration.compilerOptions ++ plugins.map(path => "-P:" + path)
      }

      module.getProject.scalaCompilerSettigns.configureFrom(compilerOptions)

      val compilerVersion = configuration.compilerVersion.get

      val matchedLibrary = modelsProvider.getAllLibraries.find(_.scalaVersion == Some(compilerVersion))

      for (library <- matchedLibrary if !library.isScalaSdk) {
        val languageLevel = ScalaLanguageLevel.from(compilerVersion).getOrElse(ScalaLanguageLevel.Default)
        val compilerClasspath = configuration.compilerClasspath.map(mavenProject.localPathTo)

        val libraryModel = modelsProvider.getLibraryModel(library).asInstanceOf[LibraryEx.ModifiableModelEx]
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

      def resolve(id: MavenId) {
        embedder.resolve(new MavenArtifactInfo(id, "pom", null), repositories)
        embedder.resolve(new MavenArtifactInfo(id, "jar", null), repositories)
      }

      configuration.compilerClasspath.foreach(resolve)
      configuration.plugins.foreach(resolve)
    }
  }

  private def validConfigurationIn(project: MavenProject) = Some(new ScalaConfiguration(project)).filter(_.valid)
}

private object ScalaMavenImporter {
  implicit class RichMavenProject(val project: MavenProject) extends AnyVal {
    def localPathTo(id: MavenId) = project.getLocalRepository / id.getGroupId.replaceAll("\\.", "/") /
            id.getArtifactId / id.getVersion / "%s-%s.jar".format(id.getArtifactId, id.getVersion)
  }

  implicit class RichFile(val file: File) extends AnyVal {
    def /(child: String): File = new File(file, child)
  }
}

private class ScalaConfiguration(project: MavenProject) {
  private def scalaCompilerId = new MavenId("org.scala-lang", "scala-compiler", compilerVersion.mkString)

  private def scalaLibraryId = new MavenId("org.scala-lang", "scala-library", compilerVersion.mkString)

  private def scalaReflectId = new MavenId("org.scala-lang", "scala-reflect", compilerVersion.mkString)

  private def compilerPlugin: Option[MavenPlugin] =
    project.findPlugin("org.scala-tools", "maven-scala-plugin").toOption.filter(!_.isDefault).orElse(
      project.findPlugin("net.alchim31.maven", "scala-maven-plugin").toOption.filter(!_.isDefault))

  private def compilerConfigurations: Seq[Element] = compilerPlugin.toSeq.flatMap { plugin =>
    plugin.getConfigurationElement.toOption.toSeq ++
            plugin.getGoalConfiguration("compile").toOption.toSeq
  }

  private def standardLibrary: Option[MavenArtifact] =
    project.findDependencies("org.scala-lang", "scala-library").headOption

  def compilerClasspath: Seq[MavenId] = {
    val basicIds = Seq(scalaCompilerId, scalaLibraryId)
    if (usesReflect) basicIds :+ scalaReflectId else basicIds
  }
  
  def compilerVersion: Option[Version] = element("scalaVersion").map(_.getTextTrim)
          .orElse(standardLibrary.map(_.getVersion)).map(Version(_))

  private def usesReflect: Boolean = compilerVersion.exists(it => ScalaLanguageLevel.from(it).exists(_ >= Scala_2_10))

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
    root.getChildren(name)

  private def element(name: String): Option[Element] =
    compilerConfigurations.flatMap(_.getChild(name).toOption.toSeq).headOption

  def valid = compilerPlugin.isDefined && compilerVersion.isDefined
}
