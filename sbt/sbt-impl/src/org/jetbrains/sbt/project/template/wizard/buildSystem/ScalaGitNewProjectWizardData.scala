package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.{ApiStatus, Nullable, TestOnly}

@deprecated("Don't use this trait. .gitignore is now added in the wizard via AssetsNewProjectWizardStep")
@ApiStatus.ScheduledForRemoval(inVersion = "2025.1")
trait ScalaGitNewProjectWizardData {
  @TestOnly
  private[project] def setGit(value: java.lang.Boolean): Unit
}

@deprecated("See the details in the trait's annotation")
object ScalaGitNewProjectWizardData {
  val KEY: Key[ScalaGitNewProjectWizardData] = Key.create(classOf[ScalaGitNewProjectWizardData].getName)

  @Nullable
  def scalaGitData(step: NewProjectWizardStep): ScalaGitNewProjectWizardData =
    step.getData.getUserData(KEY)
}
