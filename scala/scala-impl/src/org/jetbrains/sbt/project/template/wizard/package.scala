package org.jetbrains.sbt.project.template

import com.intellij.openapi.application.Experiments
import org.jetbrains.annotations.TestOnly

package object wizard {

  def isNewWizardEnabled: Boolean =
    Experiments.getInstance.isFeatureEnabled("new.project.wizard")

  @TestOnly
  def setNewWizardEnabled(enabled: Boolean): Unit =
    Experiments.getInstance.setFeatureEnabled("new.project.wizard", enabled)
}
