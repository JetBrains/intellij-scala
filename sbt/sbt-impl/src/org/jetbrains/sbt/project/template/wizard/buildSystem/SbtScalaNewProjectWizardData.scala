package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.{Nullable, TestOnly}

trait ScalaNewProjectWizardData {
  @TestOnly
  def setScalaVersion(version: String): Unit
}

object ScalaNewProjectWizardData {
  val KEY: Key[ScalaNewProjectWizardData] = Key.create(classOf[ScalaNewProjectWizardData].getName)

  @Nullable
  def scalaData(step: NewProjectWizardStep): ScalaNewProjectWizardData =
    step.getData.getUserData(KEY)
}

trait SbtScalaNewProjectWizardData extends ScalaNewProjectWizardData {
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
