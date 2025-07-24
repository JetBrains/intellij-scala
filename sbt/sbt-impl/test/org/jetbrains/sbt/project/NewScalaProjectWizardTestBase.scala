package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.NewProjectWizardTestCase
import com.intellij.ide.wizard.NewProjectWizardBaseData.getBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.plugins.scala.{SlowTests2, SlowTests}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
abstract class NewScalaProjectWizardTestBase extends NewProjectWizardTestCase
  with ProjectStructureMatcher {

  protected implicit def compareContext: ProjectStructureComparisonContext =
    ProjectStructureComparisonContext.Implicit.default(getProject)

  override protected def setUp(): Unit = {
    super.setUp()
    configureJdk()
  }

  override def tearDown(): Unit = {
    inWriteAction {
      val projectJdkTable = ProjectJdkTable.getInstance()
      projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    }
    super.tearDown()
  }

  protected def createScalaProject(
    templateGroup: String,
    projectName: String,
    checkJDK: Boolean = true
  )(configureStep: NewProjectWizardStep => Unit): Project = {
    val project = createProjectFromTemplate(
      templateGroup,
      step => {
        getBaseData(step).setName(projectName)
        configureStep(step)
      }
    )

    assertNotNull(project)

    if (checkJDK) {
      val projectJdk = ProjectRootManager.getInstance(project).getProjectSdk
      assertNotNull(projectJdk)

      val jdkVersion = JavaSdk.getInstance.getVersion(projectJdk)
      assertNotNull(jdkVersion)
      Assert.assertEquals(jdkVersion.getMaxLanguageLevel, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel)
    }

    assertNoNotificationsShown(project)

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    project
  }
}
