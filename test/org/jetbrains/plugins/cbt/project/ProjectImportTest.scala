package org.jetbrains.plugins.cbt.project

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.plugins.cbt.project.model.{CbtProjectConverter, CbtProjectInfo}
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings
import org.junit.Assert._
import org.junit.Test

import scala.util.Success

class ProjectImportTest {
  @Test
  def testSimple(): Unit = {
    val buildInfo =
      """<project name="NAME" root="ROOT" rootModule="module">
        |    <modules>
        |        <module name="rootModule" root="/projects/testProject"
        |                target="ROOT/target" scalaVersion="2.11.8" type="default">
        |            <sourceDirs>
        |                <dir>ROOT/src</dir>
        |            </sourceDirs>
        |        </module>
        |    </modules>
        |</project>""".stripMargin
    val project = importProject("testSimple", buildInfo)
    val moduleNames = ModuleManager.getInstance(project).getModules.map(_.getName).toSeq
    assertEquals(Seq("rootModule"), moduleNames)
  }

  private def importProject(name: String, buildInfo: String): Project = {
    val testFixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder(name).getFixture
    testFixture.setUp()
    val project = testFixture.getProject
    val xml = scala.xml.XML.loadString(buildInfo.replaceAllLiterally("NAME", name).replaceAllLiterally("ROOT", project.getBasePath))
    val settings = new CbtExecutionSettings(project.getBasePath, false, true, true, Seq.empty)
    Success(xml)
      .map(CbtProjectInfo(_))
      .flatMap(CbtProjectConverter(_, settings))
      .map { projectNode =>
        ServiceManager.getService(classOf[ProjectDataManager]).importData(projectNode, project, true)
      }
      .get
    project
  }
}
