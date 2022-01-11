package org.jetbrains.sbt.project.template

import com.intellij.openapi.application.Experiments

package object wizard {

  def isNewWizardEnabled: Boolean =
    Experiments.getInstance.isFeatureEnabled("new.project.wizard")
}
