package org.jetbrains.plugins.scala.config.maven

import _root_.com.intellij.openapi.diagnostic.Logger
import _root_.java.lang.String
import _root_.java.util.{List, Map}
import _root_.org.jetbrains.idea.maven.importing.{FacetImporter, MavenModifiableModelsProvider, MavenRootModelAdapter, MavenImporter}
import _root_.org.jetbrains.plugins.scala.config.{ScalaFacet, ScalaFacetConfiguration, ScalaFacetType}
import org.jetbrains.idea.maven.project._
import com.intellij.openapi.module.Module
import org.jdom.Element
import org.jetbrains.idea.maven.utils.MavenJDOMUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.03.2010
 */

class ScalaMavenImporter extends FacetImporter[ScalaFacet, ScalaFacetConfiguration, ScalaFacetType]("org.scala-tools",
  "maven-scala-plugin", ScalaFacetType.INSTANCE, "Scala") {
  private var LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.config.maven.ScalaMavenImporter")

  override def collectSourceFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "sourceDir", "src/main/scala", result)

  override def collectTestFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "testSourceDir", "src/test/scala", result)

  private def collectSourceOrTestFolders(mavenProject: MavenProject, goal: String, goalPath: String,
                                         defaultDir: String, result: java.util.List[String]): Unit = {
    val goalConfigValue = findGoalConfigValue(mavenProject, goal, goalPath)
    result.add(if (goalConfigValue == null) defaultDir else goalConfigValue)
  }

  def reimportFacet(modelsProvider: MavenModifiableModelsProvider, module: Module,
                    rootModel: MavenRootModelAdapter, facet: ScalaFacet, mavenTree: MavenProjectsTree,
                    mavenProject: MavenProject, changes: MavenProjectChanges,
                    mavenProjectToModuleName: Map[MavenProject, String],
                    postTasks: List[MavenProjectsProcessorTask]): Unit = {
    if (facet == null) {
      LOG.error("Facet null while reimport facet. Module facets: " +
              modelsProvider.getFacetModel(module).getAllFacets.map(_.getName).mkString("(", ", ", ")"))
      return
    }
    val configuration = facet.getConfiguration
    val settings = configuration.getMyScalaLibrariesConfiguration
    val compiler = mavenProject.findDependencies("org.scala-lang", "scala-compiler")
    val library = mavenProject.findDependencies("org.scala-lang", "scala-library")
    if (compiler.size > 0) {
      settings.takeFromSettings = true
      settings.myScalaCompilerJarPaths = Array(compiler.get(0).getPath)
    }
    if (library.size > 0) {
      settings.takeFromSettings = true
      settings.myScalaSdkJarPaths = Array(library.get(0).getPath)
    }
  }

  def setupFacet(f: ScalaFacet, mavenProject: MavenProject): Unit = {}
}