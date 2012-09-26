package org.jetbrains.plugins.scala
package config

import org.jetbrains.idea.maven.importing.{FacetImporter, MavenModifiableModelsProvider, MavenRootModelAdapter}
import com.intellij.openapi.module.Module
import java.util.{List, Map}
import org.jetbrains.idea.maven.project._
import com.intellij.openapi.roots.OrderRootType
import collection.JavaConversions._
import org.jdom.Element
import FileAPI._
import org.jetbrains.idea.maven.model.{MavenArtifactInfo, MavenId}
import org.jetbrains.idea.maven.server.{NativeMavenProjectHolder, MavenEmbedderWrapper}
import org.jetbrains.plugins.scala.extensions._
import com.intellij.openapi.project.Project

/**
 * Pavel.Fatin, 03.08.2010
 */

class ScalaMavenImporter extends FacetImporter[ScalaFacet, ScalaFacetConfiguration, ScalaFacetType](
  "org.scala-tools", "maven-scala-plugin", ScalaFacet.Type, "Scala") {

  private val LibraryName = "Maven: org.scala-lang:scala-compiler-bundle:%s".format(_: String)

  private implicit def toRichMavenProject(project: MavenProject) = new {
    def localPathTo(id: MavenId) = project.getLocalRepository / id.getGroupId.replaceAll("\\.", "/") /
            id.getArtifactId / id.getVersion / "%s-%s.jar".format(id.getArtifactId, id.getVersion)
  }
  
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
  override def isApplicable(mavenProject: MavenProject) =
    validConfigurationIn(mavenProject).isDefined

  def reimportFacet(modelsProvider: MavenModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter,
                    facet: ScalaFacet, mavenTree: MavenProjectsTree, mavenProject: MavenProject, 
                    changes: MavenProjectChanges, mavenProjectToModuleName: Map[MavenProject, String], 
                    postTasks: List[MavenProjectsProcessorTask]) {
    validConfigurationIn(mavenProject).foreach { configuration =>
      val name = LibraryName(configuration.compilerVersion.mkString)

      val library = modelsProvider.getLibraryByName(name).toOption.getOrElse {
        createCompilerLibrary(name, configuration, mavenProject, modelsProvider)
      }

      facet.compilerLibraryId = LibraryId(library.getName, LibraryLevel.Project)
      facet.javaParameters = configuration.vmOptions.toArray
      facet.compilerParameters = configuration.compilerOptions.toArray
      facet.pluginPaths = configuration.plugins.map(id => mavenProject.localPathTo(id).getPath).toArray
    }
  }
  
  def createCompilerLibrary(name: String, configuration: ScalaConfiguration,
                            mavenProject: MavenProject, modelsProvider: MavenModifiableModelsProvider) = {
    val compilerJarPath = mavenProject.localPathTo(configuration.compilerId)
    val standardLibraryJarPath = mavenProject.localPathTo(configuration.libraryId)

    val library = modelsProvider.createLibrary(name)

    val model = modelsProvider.getLibraryModel(library)
    model.addRoot(compilerJarPath.toLibraryRootURL, OrderRootType.CLASSES)
    model.addRoot(standardLibraryJarPath.toLibraryRootURL, OrderRootType.CLASSES)

    if (configuration.usesReflect) {
      val reflectJarPath = mavenProject.localPathTo(configuration.reflectId)
      model.addRoot(reflectJarPath.toLibraryRootURL, OrderRootType.CLASSES)
    }

    library
  }

  override def resolve(project: Project, mavenProject: MavenProject, nativeMavenProject: NativeMavenProjectHolder,
                       embedder: MavenEmbedderWrapper) {
    validConfigurationIn(mavenProject).foreach { configuration =>
      val repositories = mavenProject.getRemoteRepositories

      def resolve(id: MavenId) {
        embedder.resolve(new MavenArtifactInfo(id, "pom", null), repositories)
        embedder.resolve(new MavenArtifactInfo(id, "jar", null), repositories)
      }

      resolve(configuration.compilerId)
      resolve(configuration.libraryId)

      if (configuration.usesReflect) {
        resolve(configuration.reflectId)
      }

      configuration.plugins.foreach(resolve)
    }
  }

  private def validConfigurationIn(project: MavenProject) =
    Some(new ScalaConfiguration(project)).filter(_.valid)
  
  def setupFacet(f: ScalaFacet, mavenProject: MavenProject) {}
}

private class ScalaConfiguration(project: MavenProject) {
  def compilerId = new MavenId("org.scala-lang", "scala-compiler", compilerVersion.mkString)

  def libraryId = new MavenId("org.scala-lang", "scala-library", compilerVersion.mkString)

  def reflectId = new MavenId("org.scala-lang", "scala-reflect", compilerVersion.mkString)

  private def compilerPlugin =
    project.findPlugin("org.scala-tools", "maven-scala-plugin").toOption.filter(!_.isDefault).orElse(
      project.findPlugin("net.alchim31.maven", "scala-maven-plugin").toOption.filter(!_.isDefault))

  private def compilerConfiguration = compilerPlugin.flatMap(_.getConfigurationElement.toOption)

  private def standardLibrary = project.findDependencies("org.scala-lang", "scala-library").headOption

  def compilerVersion: Option[String] =
    element("scalaVersion").map(_.getTextTrim).orElse(standardLibrary.map(_.getVersion))

  def usesReflect: Boolean = compilerVersion.map(it => Version(it) >= Version("2.10")).getOrElse(false)

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
    root.getChildren(name).map(_.asInstanceOf[Element])

  private def element(name: String): Option[Element] =
    compilerConfiguration.flatMap(_.getChild(name).toOption)

  def valid = compilerPlugin.isDefined && compilerVersion.isDefined
}