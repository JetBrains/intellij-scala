package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.projectWizard.NewProjectWizardConstants
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard._
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import org.jetbrains.plugins.scala.icons.Icons

import javax.swing.Icon

/**
 * NOTE: see implementations of root interface [[NewProjectWizardStep]] for example
 *
 * @todo proper fields validation SCL-19509
 */
final class ScalaNewProjectWizard extends LanguageGeneratorNewProjectWizard {
  override def isEnabled(wizardContext: WizardContext): Boolean = true

  override def getName: String = NewProjectWizardConstants.Language.SCALA

  override def createStep(parentStep: NewProjectWizardStep): ScalaNewProjectWizardStep =
    new ScalaNewProjectWizardStep(parentStep)

  // Groovy is 200, we want to be right after it so in total the list is:
  // Java, Kotlin, Groovy, Scala, Python ...
  override def getOrdinal: Int = 201

  override def getIcon: Icon = Icons.SCALA_SMALL_LOGO
}
