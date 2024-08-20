package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.sbt.project.template.wizard.buildSystem.{ScalaNewProjectWizardStep, addScalaSampleCode}
import org.jetbrains.sbt.project.template.{ModuleBuilderBase, ScalaModuleBuilderSelections}
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardMultiStep

import java.nio.file.Path

/** inspired by [[com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizard]] */
final class ScalaCliNewProjectWizardStep(parent: ScalaNewProjectWizardMultiStep)
  extends ScalaNewProjectWizardStep(parent) {

  override protected val selections: ScalaModuleBuilderSelections = ScalaModuleBuilderSelections.default

  override def setupUI(panel: Panel): Unit = {
    setUpScalaUI(panel, downloadSourcesCheckbox = false)
    setUpSampleCode(panel)

    initSelectionsAndUi(getContext.getDisposable)
  }

  override protected def getSdk: Option[Sdk] = None

  override protected def createBuilder(): ModuleBuilderBase[_] =
    new ScalaCliModuleBuilder(this.selections)

  override protected def _addScalaSampleCode(project: Project, projectRoot: Path): Seq[VirtualFile] =
    addScalaSampleCode(
      project,
      projectRoot.toString,
      isScala3 = this.selections.scalaVersion.exists(_.startsWith("3.")),
      packagePrefix = None,
      withOnboardingTips = false
    )

}