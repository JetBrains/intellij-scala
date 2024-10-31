package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.{INSTANCE => BSLog}
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.{GraphProperty, ObservableProperty, PropertyGraph}
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.{ButtonKt, Panel, Row, RowLayout, TopGap}
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.ScalaSDKStepLike
import org.jetbrains.sbt.project.template.ModuleBuilderBase
import org.jetbrains.sbt.project.template.wizard.{ScalaNewProjectWizardMultiStep, ScalaVersionStepLike}

import java.nio.file.Path
import javax.swing.JLabel
import scala.annotation.nowarn

abstract class ScalaNewProjectWizardStep(parent: ScalaNewProjectWizardMultiStep)
  extends AbstractNewProjectWizardStep(parent)
    with ScalaVersionStepLike
    with ScalaSDKStepLike {

  override protected val librariesContainer: LibrariesContainer =
    LibrariesContainerFactory.createContainer(parent.getContext.getProject)

  override protected lazy val defaultAvailableScalaVersions: Versions = Versions.Scala.allHardcodedVersions

  @inline protected def propertyGraph: PropertyGraph = getPropertyGraph

  protected val moduleNameProperty: GraphProperty[String] = propertyGraph.lazyProperty(() => parent.getName)

  protected val addSampleCodeProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.FALSE)
  BindUtil.bindBooleanStorage(addSampleCodeProperty, "NewProjectWizard.addSampleCodeState")

  protected def needToAddSampleCode: Boolean = addSampleCodeProperty.get()

  protected def getSdk: Option[Sdk]
  protected def createBuilder(): ModuleBuilderBase[_]
  protected def _addScalaSampleCode(project: Project, projectRoot: Path): Seq[VirtualFile]

  locally {
    moduleNameProperty.dependsOn(parent.getNameProperty: ObservableProperty[String], (() => parent.getName): kotlin.jvm.functions.Function0[_ <: String])
  }

  override def setupProject(project: Project): Unit = {
    val builder = createBuilder()
    builder.setName(getModuleName)
    val projectRoot = getContext.getProjectDirectory.toAbsolutePath
    builder.setContentEntryPath(projectRoot.toString)

    setProjectOrModuleSdk(project, parent, builder, getSdk)

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    /** NEWLY_CREATED_PROJECT must be set up to prevent the call of markDirtyAllExternalProjects in ExternalProjectsDataStorage#load.
     * As a result, NEWLY_IMPORTED_PROJECT must also be set to keep the same behaviour as before in ExternalSystemStartupActivity.kt:48 (do not call ExternalSystemUtil#refreshProjects).
     * Similar thing is done in AbstractGradleModuleBuilder#setupModule */
    project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, java.lang.Boolean.TRUE)
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, java.lang.Boolean.TRUE)

    if (needToAddSampleCode)
      builder.openFileEditorAfterProjectOpened = _addScalaSampleCode(project, projectRoot)

    builder.commit(project)
  }

  protected def setUpScalaUI(panel: Panel, downloadSourcesCheckbox: Boolean): Unit =
    panel.row(scalaLabelText, (row: Row) => {
      row.layout(RowLayout.PARENT_GRID)
      row.cell(scalaVersionComboBox)
      if (downloadSourcesCheckbox) {
        row.cell(downloadScalaSourcesCheckbox)
      }
      KUnit
    })

  protected def setUpSampleCode(panel: Panel): Unit =
    panel.row(null: JLabel, (row: Row) => {
      val cb = row.checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
      ButtonKt.bindSelected(cb, addSampleCodeProperty: com.intellij.openapi.observable.properties.ObservableMutableProperty[java.lang.Boolean])
      ButtonKt.whenStateChangedFromUi(cb, null, value => {
        BSLog.logAddSampleCodeChanged(parent, value): @nowarn("cat=deprecation")
        KUnit
      })
      KUnit
    }).topGap(TopGap.SMALL)

  private def getModuleName: String = moduleNameProperty.get()
}
