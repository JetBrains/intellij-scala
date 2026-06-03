package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.ide.wizard.NewProjectWizardStep
import org.jetbrains.plugins.scala.util.ui.KotlinDslWrappers.StepChainOps
import org.jetbrains.sbt.project.template.wizard.{ScalaAssetsNewProjectWizardStep, ScalaNewProjectWizardMultiStep}

final class SbtScalaNewProjectWizard extends BuildSystemScalaNewProjectWizard {
  override def getName: String = NewProjectWizardConstants.BuildSystem.SBT

  override def createStep(parent: ScalaNewProjectWizardMultiStep): NewProjectWizardStep =
    new SbtScalaNewProjectWizardStep(parent)
      .nextStep(new ScalaAssetsNewProjectWizardStep(_))

  override def getOrdinal: Int = 0
}
