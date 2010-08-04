package org.jetbrains.plugins.scala.config

import org.jetbrains.idea.maven.importing.{FacetImporter, MavenModifiableModelsProvider, MavenRootModelAdapter}
import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper
import org.apache.maven.project.{MavenProject => NativeMavenProject}
import java.util.{List, Map}
import java.lang.String
import org.jetbrains.idea.maven.project._
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer.LibraryLevel

/**
 * Pavel.Fatin, 03.08.2010
 */

class ScalaMavenImporter extends FacetImporter[ScalaFacet, ScalaFacetConfiguration, ScalaFacetType](
  "org.scala-tools", "maven-scala-plugin", ScalaFacet.Type, "Scala") {
  
  override def collectSourceFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "sourceDir", "src/main/scala", result)

  override def collectTestFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "testSourceDir", "src/test/scala", result)

  private def collectSourceOrTestFolders(mavenProject: MavenProject, goal: String, goalPath: String,
                                         defaultDir: String, result: java.util.List[String]): Unit = {
    val goalConfigValue = findGoalConfigValue(mavenProject, goal, goalPath)
    result.add(if (goalConfigValue == null) defaultDir else goalConfigValue)
  }


  def reimportFacet(modelsProvider: MavenModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter, 
                    facet: ScalaFacet, mavenTree: MavenProjectsTree, mavenProject: MavenProject, 
                    changes: MavenProjectChanges, mavenProjectToModuleName: Map[MavenProject, String], 
                    postTasks: List[MavenProjectsProcessorTask]) = {
    scalaVersionIn(mavenProject).foreach { version =>
      val repositoryPath = mavenProject.getLocalRepository.getPath + "/org/scala-lang"
      val compilerPath = repositoryPath + "/scala-compiler/%1$s/scala-compiler-%1$s.jar".format(version)
      val libraryPath = repositoryPath + "/scala-library/%1$s/scala-library-%1$s.jar".format(version)
      
      val library = modelsProvider.createLibrary("maven: org.scala-lang:scala-compiler:" + version)
      val model = modelsProvider.getLibraryModel(library)
      model.addRoot(VfsUtil.pathToUrl(compilerPath), OrderRootType.CLASSES)
      model.addRoot(VfsUtil.pathToUrl(libraryPath), OrderRootType.CLASSES)
      
      val settings = facet.getConfiguration.getState
      settings.compilerLibraryName = library.getName
      settings.compilerLibraryLevel = LibraryLevel.PROJECT
    }
  }

  override def resolve(project: MavenProject, nativeMavenProject: NativeMavenProject, embedder: MavenEmbedderWrapper) = {
    val repositories = project.getRemoteRepositories
    scalaVersionIn(project).foreach { version =>
      embedder.resolve(new MavenId("org.scala-lang", "scala-compiler", version), "pom", null, repositories)
      embedder.resolve(new MavenId("org.scala-lang", "scala-compiler", version), "jar", null, repositories)
    }
  }
  
  private def scalaVersionIn(project: MavenProject): Option[String] = 
    Option(findConfigValue(project, "scalaVersion"))
  
  def setupFacet(f: ScalaFacet, mavenProject: MavenProject): Unit = {}
}