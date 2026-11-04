package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.wizard.{NewProjectWizardChainStep, NewProjectWizardStep}

object NewProjectWizardUtil {
  implicit final class StepChainOps[Step <: NewProjectWizardStep](private val step: Step) extends AnyVal {
    def nextStep[NextStep <: NewProjectWizardStep](createNextStep: Step => NextStep): NewProjectWizardChainStep[NextStep] =
      NewProjectWizardChainStep.Companion.nextStep[Step, NextStep](step, createNextStep(_))
  }
}
