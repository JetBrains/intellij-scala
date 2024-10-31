package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.ide.wizard.NewProjectWizardStep
import org.jetbrains.plugins.scala.util.ui.KotlinDslWrappers.StepChainOps
import org.jetbrains.sbt.project.template.wizard.{ScalaAssetsNewProjectWizardStep, ScalaNewProjectWizardMultiStep}

final class IntelliJScalaNewProjectWizard extends BuildSystemScalaNewProjectWizard {
  override val getName: String = NewProjectWizardConstants.BuildSystem.INTELLIJ

  override def createStep(parent: ScalaNewProjectWizardMultiStep): NewProjectWizardStep =
    new IntelliJScalaNewProjectWizardStep(parent)
      .nextStep(new ScalaAssetsNewProjectWizardStep(_))

  override def getOrdinal: Int = 1
}
