package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardStep

/** analog of [[com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard]] */
trait BuildSystemScalaNewProjectWizard extends NewProjectWizardMultiStepFactory[ScalaNewProjectWizardStep] {
  override def isEnabled(wizardContext: WizardContext): Boolean = true
}

object BuildSystemScalaNewProjectWizard {
  var EP_NAME = new ExtensionPointName[BuildSystemScalaNewProjectWizard]("com.intellij.newProjectWizard.scala.buildSystem")
}
