package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.NewProjectWizardTestCase
import com.intellij.ide.wizard.NewProjectWizardBaseData.getBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
abstract class NewScalaProjectWizardTestBase extends NewProjectWizardTestCase
  with ProjectStructureMatcher {

  protected implicit def comparisonOptions: ProjectComparisonOptions =
    ProjectComparisonOptions.Implicit.default

  override protected def setUp(): Unit = {
    super.setUp()
    configureJdk()
  }

  protected def createScalaProject(
    templateGroup: String,
    projectName: String,
  )(configureStep: NewProjectWizardStep => Unit): Project = {
    val project = createProjectFromTemplate(
      templateGroup,
      step => {
        getBaseData(step).setName(projectName)
        configureStep(step)
      }
    )

    assertNotNull(project)

    val projectJdk = ProjectRootManager.getInstance(project).getProjectSdk
    assertNotNull(projectJdk)

    val jdkVersion = JavaSdk.getInstance.getVersion(projectJdk)
    assertNotNull(jdkVersion)
    Assert.assertEquals(jdkVersion.getMaxLanguageLevel, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel)

    assertNoNotificationsShown(project)

    project
  }
}
