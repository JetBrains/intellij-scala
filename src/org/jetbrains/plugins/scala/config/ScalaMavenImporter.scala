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
import org.jetbrains.idea.maven.facade.{NativeMavenProjectHolder, MavenEmbedderWrapper}

/**
 * Pavel.Fatin, 03.08.2010
 */

class ScalaMavenImporter extends FacetImporter[ScalaFacet, ScalaFacetConfiguration, ScalaFacetType](
  "org.scala-tools", "maven-scala-plugin", ScalaFacet.Type, "Scala") {
  
  private implicit def toRichMavenProject(project: MavenProject) = new {
    def localPathTo(id: MavenId) = project.getLocalRepository / id.getGroupId.replaceAll("\\.", "/") /
            id.getArtifactId / id.getVersion / "%s-%s.jar".format(id.getArtifactId, id.getVersion)
  }
  
  override def collectSourceFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "sourceDir", "src/main/scala", result)

  override def collectTestFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "testSourceDir", "src/test/scala", result)

  private def collectSourceOrTestFolders(mavenProject: MavenProject, goal: String, goalPath: String,
                                         defaultDir: String, result: java.util.List[String]): Unit = {
    val goalConfigValue = findGoalConfigValue(mavenProject, goal, goalPath)
    result.add(if (goalConfigValue == null) defaultDir else goalConfigValue)
  }

  override def isApplicable(mavenProject: MavenProject) = super.isApplicable(mavenProject) && mavenProject.getPackaging != "pom"

  def reimportFacet(modelsProvider: MavenModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter,
                    facet: ScalaFacet, mavenTree: MavenProjectsTree, mavenProject: MavenProject, 
                    changes: MavenProjectChanges, mavenProjectToModuleName: Map[MavenProject, String], 
                    postTasks: List[MavenProjectsProcessorTask]) = {
    validConfigurationIn(mavenProject).foreach { configuration =>
      val libraryName = "Maven: org.scala-lang:scala-compiler-bundle:" + configuration.compilerVersion.mkString
      
      val library = {
        val existingLibrary = modelsProvider.getLibraryByName(libraryName)
        if(existingLibrary == null)
          createCompilerLibrary(libraryName, configuration, mavenProject, modelsProvider)
        else
          existingLibrary
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

    library
  }

  override def resolve(project: MavenProject, nativeMavenProject: NativeMavenProjectHolder, embedder: MavenEmbedderWrapper) = {
    validConfigurationIn(project).foreach { configuration =>
      val repositories = project.getRemoteRepositories

      val compilerId = configuration.compilerId
      embedder.resolve(new MavenArtifactInfo(compilerId, "pom", null), repositories)
      embedder.resolve(new MavenArtifactInfo(compilerId, "jar", null), repositories)
      
      val libraryId = configuration.libraryId
      embedder.resolve(new MavenArtifactInfo(libraryId, "pom", null), repositories)
      embedder.resolve(new MavenArtifactInfo(libraryId, "jar", null), repositories)
      
      configuration.plugins.foreach { pluginId =>
        embedder.resolve(new MavenArtifactInfo(pluginId, "pom", null), repositories)
        embedder.resolve(new MavenArtifactInfo(pluginId, "jar", null), repositories)
      }
    }
  }
  
  private def validConfigurationIn(project: MavenProject) =
    Some(new ScalaConfiguration(project)).filter(_.valid)
  
  def setupFacet(f: ScalaFacet, mavenProject: MavenProject): Unit = {}
}

private class ScalaConfiguration(project: MavenProject) {
  def compilerId = new MavenId("org.scala-lang", "scala-compiler", compilerVersion.mkString)

  def libraryId = new MavenId("org.scala-lang", "scala-library", compilerVersion.mkString)

  private def compilerConfiguration =
    project.findPlugin("org.scala-tools", "maven-scala-plugin").toOption.flatMap(_.getConfigurationElement.toOption)

  private def standardLibrary = project.findDependencies("org.scala-lang", "scala-library").headOption

  def compilerVersion: Option[String] =
    element("scalaVersion").map(_.getTextTrim).orElse(standardLibrary.map(_.getVersion))

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

  def valid = compilerVersion.isDefined
}