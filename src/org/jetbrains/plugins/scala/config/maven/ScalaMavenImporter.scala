package org.jetbrains.plugins.scala.config.maven

import org.jetbrains.idea.maven.project._
import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.importing.{MavenModifiableModelsProvider, MavenRootModelAdapter, MavenImporter}
import org.jdom.Element
import org.jetbrains.idea.maven.utils.MavenJDOMUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.03.2010
 */

class ScalaMavenImporter extends MavenImporter("org.scala-tools", "maven-scala-plugin") {
  def isSupportedDependency(artifact: MavenArtifact): Boolean = false
  def preProcess(module: Module, mavenProject: MavenProject, changes: MavenProjectChanges,
                 modifiableModelsProvider: MavenModifiableModelsProvider): Unit = {}
  def process(modifiableModelsProvider: MavenModifiableModelsProvider, module: Module, rootModel: MavenRootModelAdapter,
              mavenModel: MavenProjectsTree, mavenProject: MavenProject, changes: MavenProjectChanges,
              mavenProjectToModuleName: java.util.Map[MavenProject, String],
              postTasks: java.util.List[MavenProjectsProcessorTask]): Unit = {}

  override def collectSourceFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "sourceDir", "src/main/scala", result)

  override def collectTestFolders(mavenProject: MavenProject, result: java.util.List[String]): Unit =
    collectSourceOrTestFolders(mavenProject, "add-source", "testSourceDir", "src/test/scala", result)

  private def collectSourceOrTestFolders(mavenProject: MavenProject, goal: String, goalPath: String,
                                         defaultDir: String, result: java.util.List[String]): Unit = {
    val goalConfigValue = findGoalConfigValue(mavenProject, goal, goalPath)
    result.add(if (goalConfigValue == null) defaultDir else goalConfigValue)
  }
}