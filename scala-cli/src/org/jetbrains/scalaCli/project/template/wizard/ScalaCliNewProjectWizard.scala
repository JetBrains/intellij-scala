package org.jetbrains.scalaCli.project.template.wizard

import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardMultiStep
import org.jetbrains.sbt.project.template.wizard.buildSystem.BuildSystemScalaNewProjectWizard
import org.jetbrains.scalaCli.ScalaCliBundle

final class ScalaCliNewProjectWizard extends BuildSystemScalaNewProjectWizard {

  override val getName: String = ScalaCliBundle.message("scala.cli.project.system.readable.name")

  override def createStep(parent: ScalaNewProjectWizardMultiStep): ScalaCliNewProjectWizardStep =
    new ScalaCliNewProjectWizardStep(parent)

  override def getOrdinal: Int = 1
}
