package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project

import scala.jdk.CollectionConverters.IterableHasAsJava

//noinspection ApiStatus,UnstableApiUsage
class ScalaAssetsNewProjectWizardStep(parent: NewProjectWizardStep) extends AssetsNewProjectWizardStep(parent) {
  override def setupAssets(project: Project): Unit = {
    if (getContext.isCreatingNewProject) {
      addAssets(new AssetsProvider().getScalaIgnoreAssets.asJava)
    }
    // TODO: move onboarding tips and sample code creation to the assets step
  }
}
