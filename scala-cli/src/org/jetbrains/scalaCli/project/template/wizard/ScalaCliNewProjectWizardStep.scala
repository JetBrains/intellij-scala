package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.wizard.{AbstractNewProjectWizardStep, CommitStepException}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.{GraphProperty, ObservableProperty, PropertyGraph}
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.{ButtonKt, Panel, Row, TopGap}
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.ScalaSDKStepLike
import org.jetbrains.plugins.scala.util.ui.extensions.JComboBoxOps
import org.jetbrains.sbt.project.template.ScalaModuleBuilderSelections
import org.jetbrains.sbt.project.template.wizard.buildSystem.{ScalaNewProjectWizardData, ScalaSampleCodeNewProjectWizardData, addScalaSampleCode}
import org.jetbrains.sbt.project.template.wizard.{ScalaNewProjectWizardMultiStep, ScalaVersionStepLike}
import org.jetbrains.scalaCli.{ScalaCliBundle, ScalaCliUtils}

import java.nio.file.Path
import javax.swing.JLabel
import kotlin.Unit.{INSTANCE => KUnit}

/** inspired by [[com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizard]] */
final class ScalaCliNewProjectWizardStep(parent: ScalaNewProjectWizardMultiStep)
  extends AbstractNewProjectWizardStep(parent)
    with ScalaNewProjectWizardData
    with ScalaSampleCodeNewProjectWizardData
    with ScalaVersionStepLike
    with ScalaSDKStepLike {

  override protected val defaultAvailableScalaVersions: Seq[String] = Versions.Scala.allHardcodedVersions.map(_.presentation)

  override protected val librariesContainer: LibrariesContainer =
    LibrariesContainerFactory.createContainer(parent.getContext.getProject)

  @inline private def propertyGraph: PropertyGraph = getPropertyGraph

  private val moduleNameProperty: GraphProperty[String] = propertyGraph.lazyProperty(() => parent.getName)
  private def getModuleName: String = moduleNameProperty.get()

  private val addSampleCodeProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.FALSE)
  BindUtil.bindBooleanStorage(addSampleCodeProperty, "NewProjectWizard.addSampleCodeState")
  private def needToAddSampleCode: Boolean = addSampleCodeProperty.get()

  @TestOnly override def setScalaVersion(version: String): Unit = scalaVersionComboBox.setSelectedItemEnsuring(version)
  @TestOnly override def setAddSampleCode(value: java.lang.Boolean): Unit = addSampleCodeProperty.set(value)

  override protected val selections: ScalaModuleBuilderSelections = ScalaModuleBuilderSelections.default

  locally {
    moduleNameProperty.dependsOn(parent.getNameProperty: ObservableProperty[String], (() => parent.getName): kotlin.jvm.functions.Function0[_ <: String])

    getData.putUserData(ScalaNewProjectWizardData.KEY, this)
    getData.putUserData(ScalaSampleCodeNewProjectWizardData.KEY, this)
  }

  override def setupProject(project: Project): Unit = {
    val builder = new ScalaCliModuleBuilder(this.selections)
    builder.setName(getModuleName)
    val projectRoot = getContext.getProjectDirectory.toAbsolutePath
    builder.setContentEntryPath(projectRoot.toString)

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    /** NEWLY_CREATED_PROJECT must be set up to prevent the call of markDirtyAllExternalProjects in ExternalProjectsDataStorage#load.
     * As a result, NEWLY_IMPORTED_PROJECT must also be set to keep the same behaviour as before in ExternalSystemStartupActivity.kt:48 (do not call ExternalSystemUtil#refreshProjects).
     * Similar thing is done in AbstractGradleModuleBuilder#setupModule */
    project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, java.lang.Boolean.TRUE)
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, java.lang.Boolean.TRUE)

    if (needToAddSampleCode) {
      val files = addScalaSampleCode(
        project,
        projectRoot.toString,
        isScala3 = this.selections.scalaVersion.exists(_.startsWith("3.")),
        packagePrefix = None,
        withOnboardingTips = false
      )
      builder.openFileEditorAfterProjectOpened = files
    }

    builder.commit(project)
  }

  override def setupUI(panel: Panel): Unit = {
    panel.onApply(() => {
      // note: if these are tests, `getContext.getProjectDirectory` does not return the exact root directory of the project.
      // During tests, only the exact root directory contains the Scala CLI run script.
      // Therefore, we cannot execute `throwExceptionIfScalaCliNotInstalled`, as it will fail.
      // But it's not really required for tests because checking if Scala CLI is installed is also done in ScalaCliProjectInstaller#installCommand.
      if (!ApplicationManager.getApplication.isUnitTestMode) {
        val isScalaCliInstalled = ScalaCliUtils.isScalaCliInstalled(Path.of(SystemProperties.getUserHome))
        if (!isScalaCliInstalled) {
          throw new CommitStepException(ScalaCliBundle.message("scala.cli.not.installed"))
        }
      }
      KUnit
    })

    setUpScalaUI(panel, downloadSourcesCheckbox = false)

    panel.row(null: JLabel, (row: Row) => {
      val cb = row.checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
      ButtonKt.bindSelected(cb, addSampleCodeProperty: com.intellij.openapi.observable.properties.ObservableMutableProperty[java.lang.Boolean])
      ButtonKt.whenStateChangedFromUi(cb, null, value => {
        NewProjectWizardCollector.Base.INSTANCE.logAddSampleCodeChanged(parent, value)
        KUnit
      })
      KUnit
    }).topGap(TopGap.SMALL)

    initSelectionsAndUi(getContext.getDisposable)
  }
}
