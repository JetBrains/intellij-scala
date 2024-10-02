package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.ide.wizard.CommitStepException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import kotlin.Unit.{INSTANCE => KUnit}
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.util.ui.extensions.JComboBoxOps
import org.jetbrains.sbt.project.template.wizard.buildSystem.{ScalaNewProjectWizardData, ScalaNewProjectWizardStep, ScalaSampleCodeNewProjectWizardData, addScalaSampleCode}
import org.jetbrains.sbt.project.template.{ModuleBuilderBase, ScalaModuleBuilderSelections}
import org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizardMultiStep
import org.jetbrains.scalaCli.{ScalaCliBundle, ScalaCliUtils}

import java.nio.file.Path

/** inspired by [[com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizard]] */
final class ScalaCliNewProjectWizardStep(parent: ScalaNewProjectWizardMultiStep)
  extends ScalaNewProjectWizardStep(parent)
  with ScalaNewProjectWizardData
  with ScalaSampleCodeNewProjectWizardData {

  locally {
    getData.putUserData(ScalaNewProjectWizardData.KEY, this)
    getData.putUserData(ScalaSampleCodeNewProjectWizardData.KEY, this)
  }

  @TestOnly override def setScalaVersion(version: String): Unit = scalaVersionComboBox.setSelectedItemEnsuring(version)
  @TestOnly override def setAddSampleCode(value: java.lang.Boolean): Unit = addSampleCodeProperty.set(value)
  @TestOnly override def setGenerateOnboardingTips(value: java.lang.Boolean): Unit = ()

  override protected val selections: ScalaModuleBuilderSelections = ScalaModuleBuilderSelections.default

  override def setupUI(panel: Panel): Unit = {
    panel.onApply(() => {
      // note: if these are tests, `getContext.getProjectDirectory` does not return the exact root directory of the project.
      // During tests, only the exact root directory contains the Scala CLI run script.
      // Therefore, we cannot execute `throwExceptionIfScalaCliNotInstalled`, as it will fail.
      // But it's not really required for tests because checking if Scala CLI is installed is also done in ScalaCliProjectInstaller#installCommand.
      if (!ApplicationManager.getApplication.isUnitTestMode) {
        val isScalaCliInstalled = ScalaCliUtils.isScalaCliInstalled(getContext.getProjectDirectory.toFile)
        if (!isScalaCliInstalled) {
          throw new CommitStepException(ScalaCliBundle.message("scala.cli.not.installed"))
        }
      }
      KUnit
    })

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