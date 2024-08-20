package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.{INSTANCE => BSLog}
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.properties.{GraphProperty, ObservableProperty, PropertyGraph}
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.{ButtonKt, Panel, Row, RowLayout, TopGap}
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.ScalaSDKStepLike
import org.jetbrains.sbt.project.template.wizard.buildSystem.addScalaSampleCode
import org.jetbrains.sbt.project.template.SbtModuleBuilderSelections
import org.jetbrains.sbt.project.template.wizard.{ScalaModuleStepLike, ScalaNewProjectWizardStep}

import javax.swing.JLabel
import scala.annotation.nowarn

/** inspired by [[com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizard]] */
final class ScalaCliNewProjectWizardStep(parent: ScalaNewProjectWizardStep)
  extends AbstractNewProjectWizardStep(parent)
    with ScalaModuleStepLike
    with ScalaSDKStepLike {

  override protected val selections: SbtModuleBuilderSelections = SbtModuleBuilderSelections.default

  override protected lazy val defaultAvailableScalaVersions: Versions = Versions.Scala.allHardcodedVersions

  override protected val librariesContainer: LibrariesContainer =
    LibrariesContainerFactory.createContainer(parent.getContext.getProject)

  @inline private def propertyGraph: PropertyGraph = getPropertyGraph

  private val addSampleCodeProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.FALSE)
  BindUtil.bindBooleanStorage(addSampleCodeProperty, "NewProjectWizard.addSampleCodeState")

  private val moduleNameProperty: GraphProperty[String] = propertyGraph.lazyProperty(() => parent.getName)

  private def needToAddSampleCode: Boolean = addSampleCodeProperty.get()
  def getModuleName: String = moduleNameProperty.get()

  locally {
    moduleNameProperty.dependsOn(parent.getNameProperty: ObservableProperty[String], (() => parent.getName): kotlin.jvm.functions.Function0[_ <: String])
  }

  override def setupProject(project: Project): Unit = {
    val builder = new ScalaCliModuleBuilder(this.selections)
    builder.setName(getModuleName)
    val projectRoot = getContext.getProjectDirectory.toAbsolutePath.toString
    builder.setContentEntryPath(projectRoot)

//    setProjectOrModuleSdk(project, parent, builder, Option(getSdk))

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, java.lang.Boolean.TRUE)
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, java.lang.Boolean.TRUE)

    if (needToAddSampleCode) {
      val file = addScalaSampleCode(project, projectRoot, isScala3 = this.selections.scalaVersion.exists(_.startsWith("3.")), this.selections.packagePrefix, withOnboardingTips = false)
      builder.openFileEditorAfterProjectOpened = Some(file)
    }

    builder.commit(project)
  }

  override def setupUI(panel: Panel): Unit = {
    panel.row(scalaLabelText, (row: Row) => {
      row.layout(RowLayout.PARENT_GRID)
      row.cell(scalaVersionComboBox).horizontalAlign(HorizontalAlign.FILL): @nowarn("cat=deprecation")
      row.cell(downloadScalaSourcesCheckbox)
      KUnit
    })

    panel.row(null: JLabel, (row: Row) => {
      val cb = row.checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
      ButtonKt.bindSelected(cb, addSampleCodeProperty: com.intellij.openapi.observable.properties.ObservableMutableProperty[java.lang.Boolean])
      ButtonKt.whenStateChangedFromUi(cb, null, value => {
        BSLog.logAddSampleCodeChanged(parent, value): @nowarn("cat=deprecation")
        KUnit
      })
      KUnit
    }).topGap(TopGap.SMALL)

    initSelectionsAndUi(getContext.getDisposable)
    super.setupUI(panel)
  }

}