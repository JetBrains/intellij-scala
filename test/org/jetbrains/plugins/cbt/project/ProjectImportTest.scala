package org.jetbrains.plugins.cbt.project

import java.io.File

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.model.{CbtProjectConverter, CbtProjectInfo}
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.junit.Assert._
import org.junit.Test
class ProjectImportTest {
  @Test
  def testSimple(): Unit = {
    import CbtProjectInfo._
    val projectInfo = Project(name = "NAME",
      root = "ROOT".toFile,
      modules = Seq(Module(
        name = "rootModule",
        root = "ROOT".toFile,
        sourceDirs = Seq.empty,
        scalaVersion = "2.11.8",
        target = "ROOT/target".toFile,
        moduleType = ModuleType.Default,
        binaryDependencies = Seq.empty,
        moduleDependencies = Seq.empty,
        classpath = Seq.empty,
        parentBuild = None,
        scalacOptions = Seq.empty
      )),
      libraries = Seq.empty,
      cbtLibraries = Seq.empty,
      scalaCompilers = Seq.empty)

    val project = importProject("testSimple", projectInfo)
    val actualModuleNames = ModuleManager.getInstance(project).getModules.map(_.getName).toSeq
    val expectedModuleNames = projectInfo.modules.map(_.name)
    assertEquals(expectedModuleNames, actualModuleNames)
  }

  def replacePaths(project: CbtProjectInfo.Project, name: String, path: String): CbtProjectInfo.Project = {
    import CbtProjectInfo._
    def replace(obj: AnyRef): AnyRef = obj match {
      case project: Project =>
        project.copy(root = replace(project.root).asInstanceOf[File],
          modules = project.modules.map(replace(_).asInstanceOf[Module]),
          libraries = project.libraries.map(replace(_).asInstanceOf[Library]),
          cbtLibraries = project.cbtLibraries.map(replace(_).asInstanceOf[CbtProjectInfo.Library]),
          scalaCompilers = project.scalaCompilers.map(replace(_).asInstanceOf[CbtProjectInfo.ScalaCompiler])
        )
      case module: Module =>
        module.copy(root = replace(module.root).asInstanceOf[File],
          sourceDirs = module.sourceDirs.map(replace(_).asInstanceOf[File]),
          target = replace(module.target).asInstanceOf[File]
        )
      case file: File =>
        file.getPath.replaceAllLiterally("NAME", name).replaceAllLiterally("ROOT", path).toFile
    }

    replace(project).asInstanceOf[Project]
  }
  private def importProject(name: String, projectInfo: CbtProjectInfo.Project): project.Project = {
    val testFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(name).getFixture
    testFixture.setUp()
    val project = testFixture.getProject
    val settings = new CbtExecutionSettings(project.getBasePath, false, true, true, Seq.empty)
    CbtProjectConverter(replacePaths(projectInfo, name, project.getBasePath), settings)
      .map { projectNode =>
        ServiceManager.getService(classOf[ProjectDataManager]).importData(projectNode, project, true)
      }
      .get
    project
  }
}
