package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.{Nullable, TestOnly}

trait SbtScalaNewProjectWizardData {
  @TestOnly
  private[project] def setScalaVersion(version: String): Unit

  @TestOnly
  private[project] def setSbtVersion(version: String): Unit

  @TestOnly
  private[project] def setPackagePrefix(prefix: String): Unit
}

object SbtScalaNewProjectWizardData {
  val KEY: Key[SbtScalaNewProjectWizardData] = Key.create(classOf[SbtScalaNewProjectWizardData].getName)

  @Nullable
  def scalaSbtData(step: NewProjectWizardStep): SbtScalaNewProjectWizardData =
    step.getData.getUserData(KEY)
}
