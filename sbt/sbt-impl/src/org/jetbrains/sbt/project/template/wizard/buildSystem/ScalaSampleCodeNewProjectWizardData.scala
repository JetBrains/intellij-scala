package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.{Nullable, TestOnly}

trait ScalaSampleCodeNewProjectWizardData {
  @TestOnly
  private[project] def setAddSampleCode(value: java.lang.Boolean): Unit
}

object ScalaSampleCodeNewProjectWizardData {
  val KEY: Key[ScalaSampleCodeNewProjectWizardData] = Key.create(classOf[ScalaSampleCodeNewProjectWizardData].getName)

  @Nullable
  def scalaSampleCodeData(step: NewProjectWizardStep): ScalaSampleCodeNewProjectWizardData =
    step.getData.getUserData(KEY)
}
