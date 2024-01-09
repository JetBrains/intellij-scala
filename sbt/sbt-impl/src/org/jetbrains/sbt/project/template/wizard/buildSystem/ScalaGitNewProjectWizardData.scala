package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.{Nullable, TestOnly}

trait ScalaGitNewProjectWizardData {
  @TestOnly
  private[project] def setGit(value: java.lang.Boolean): Unit
}

object ScalaGitNewProjectWizardData {
  val KEY: Key[ScalaGitNewProjectWizardData] = Key.create(classOf[ScalaGitNewProjectWizardData].getName)

  @Nullable
  def scalaGitData(step: NewProjectWizardStep): ScalaGitNewProjectWizardData =
    step.getData.getUserData(KEY)
}
