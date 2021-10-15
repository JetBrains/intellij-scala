package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard._
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizard.ScalaLanguageText

/**
 * NOTE: see implementations of root interface [[NewProjectWizardStep]] for example
 *
 * @todo proper fields validation SCL-19509
 */
final class ScalaNewProjectWizard extends LanguageNewProjectWizard {
  override def isEnabled(wizardContext: WizardContext): Boolean = true

  override def getName: String = ScalaLanguageText

  override def createStep(parentStep: NewProjectWizardLanguageStep): ScalaNewProjectWizardStep =
    new ScalaNewProjectWizardStep(parentStep)
}

object ScalaNewProjectWizard {
  private[wizard] val ScalaLanguageText = "Scala"
}

