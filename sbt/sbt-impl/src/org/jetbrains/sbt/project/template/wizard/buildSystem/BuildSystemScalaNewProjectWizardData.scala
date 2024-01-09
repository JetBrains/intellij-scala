package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.wizard.{BuildSystemNewProjectWizardData, NewProjectWizardStep}
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Nullable

/** analog of [[com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData]] */
trait BuildSystemScalaNewProjectWizardData extends BuildSystemNewProjectWizardData

object BuildSystemScalaNewProjectWizardData {
  val KEY: Key[BuildSystemScalaNewProjectWizardData] = Key.create(classOf[BuildSystemScalaNewProjectWizardData].getName)

  @Nullable
  def scalaBuildSystemData(step: NewProjectWizardStep): BuildSystemScalaNewProjectWizardData =
    step.getData.getUserData(KEY)
}
