package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.{ChooseTemplateStep, NewProjectWizardTestCase, ProjectSettingsStep, ProjectTypeStep}
import com.intellij.ide.util.projectWizard.SdkSettingsStep
import com.intellij.ide.wizard.Step
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.project.template
import org.jetbrains.sbt.project.ProjectStructureMatcher.ProjectComparisonOptions
import org.junit.Assert
import org.junit.Assert.{assertNotNull, assertTrue, fail}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.{ClassTag, classTag}

@Category(Array(classOf[SlowTests]))
abstract class NewScalaProjectWizardTestBase extends NewProjectWizardTestCase
  with ProjectStructureMatcher {

  protected implicit def comparisonOptions: ProjectComparisonOptions =
    ProjectComparisonOptions.Implicit.default

  //TODO: remove this and rewrite new project wizard tests when NPW is stable and enabled by default in IDEA Release versions
  private var npwWasEnabled = false

  override protected def setUp(): Unit = {
    super.setUp()
    configureJdk()

    npwWasEnabled = template.isNewWizardEnabled
    template.setNewWizardEnabled(false)
  }

  override def tearDown(): Unit = {
    template.setNewWizardEnabled(npwWasEnabled)
    super.tearDown()
  }

  protected def createScalaProject(
    templateName: String,
    projectName: String,
  )(stepAdjuster: java.util.function.Consumer[_ >: Step]): Project = {
    val project = createProject { step =>
      step match {
        case projectTypeStep: ProjectTypeStep =>
          assertTrue(projectTypeStep.setSelectedTemplate("Scala", templateName))

          val steps = myWizard.getSequence.getSelectedSteps.asScala.map(_.getClass)
          val commonScalaProjectWizardSteps = Seq(classOf[ProjectTypeStep], classOf[ChooseTemplateStep], classOf[ProjectSettingsStep])
          Assert.assertEquals(commonScalaProjectWizardSteps, steps)

        case projectSettingsStep: ProjectSettingsStep =>
          projectSettingsStep.setNameValue(projectName)
      }

      stepAdjuster.accept(step)
    }

    assertNotNull(project)

    val projectJdk= ProjectRootManager.getInstance(project).getProjectSdk
    assertNotNull(projectJdk)

    val jdkVersion = JavaSdk.getInstance.getVersion(projectJdk)
    assertNotNull(jdkVersion)
    Assert.assertEquals(jdkVersion.getMaxLanguageLevel, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel)

    assertNoNotificationsShown(project)

    project
  }

  protected implicit class SdkSettingsStepOps(private val projectSettingsStep: ProjectSettingsStep)  {
    def getSettingsStepTyped[T <: SdkSettingsStep : ClassTag]: T =
      projectSettingsStep.getSettingsStep match {
        case s: T => s
        case unknownStep =>
          fail(s"Expected project step '${classTag[T].getClass.getName}' but got '$unknownStep''").asInstanceOf[Nothing]
      }
  }
}
