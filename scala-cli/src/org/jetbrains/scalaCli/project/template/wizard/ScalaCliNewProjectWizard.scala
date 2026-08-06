package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.ide.wizard.NewProjectWizardStep
import org.jetbrains.plugins.scala.util.ui.KotlinDslWrappers.StepChainOps
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizard
import org.jetbrains.sbt.project.template.wizard.{ScalaAssetsNewProjectWizardStep, ScalaNewProjectWizardMultiStep}
import org.jetbrains.scalaCli.ScalaCliBundle

final class ScalaCliNewProjectWizard extends BuildSystemScalaNewProjectWizard {

  override val getName: String = ScalaCliBundle.message("scala.cli.project.system.readable.name")

  override def createStep(parent: ScalaNewProjectWizardMultiStep): NewProjectWizardStep =
    new ScalaCliNewProjectWizardStep(parent)
      .nextStep(new ScalaAssetsNewProjectWizardStep(_))

  override def getOrdinal: Int = 1
}
