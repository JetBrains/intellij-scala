package org.jetbrains.scalaCli.project.template.wizard

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
import org.jetbrains.scalaCli.ScalaCliUtils

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
      ScalaCliUtils.throwExceptionIfScalaCliNotInstalled(getContext.getProjectDirectory.toFile)
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