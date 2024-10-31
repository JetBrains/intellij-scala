package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.{JavaUiBundle, RecentProjectsManagerBase}
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.{INSTANCE => BSLog}
import com.intellij.openapi.module.{ModuleManager, StdModuleTypes}
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.{JavaSdkType, Sdk, SdkTypeId}
import com.intellij.openapi.roots.ui.configuration.{JdkComboBox, ProjectStructureConfigurable}
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder._
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.ValidationInfoBuilder
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ToNullSafe}
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.util.ui.extensions.JComboBoxOps
import org.jetbrains.sbt.project.template.wizard.kotlin_interop.{ComboBoxKt_Wrapper, JdkComboBoxKt_Interop}
import org.jetbrains.sbt.project.template.wizard.{SbtModuleStepLike, ScalaNewProjectWizardMultiStep}
import org.jetbrains.sbt.project.template.{ModuleBuilderBase, SbtModuleBuilder, SbtModuleBuilderSelections}

import java.nio.file.Path
import javax.swing.JLabel
import scala.annotation.nowarn

//noinspection ApiStatus,UnstableApiUsage
final class SbtScalaNewProjectWizardStep(parent: ScalaNewProjectWizardMultiStep)
  extends ScalaNewProjectWizardStep(parent)
    with SbtScalaNewProjectWizardData
    with ScalaSampleCodeNewProjectWizardData
    with SbtModuleStepLike {

  private var sdkComboBox: Cell[JdkComboBox] = _
  private val sdkProperty: GraphProperty[Sdk] = propertyGraph.property(null)

  @TestOnly override private[project] def setAddSampleCode(value: java.lang.Boolean): Unit = addSampleCodeProperty.set(value)

  private val generateOnboardingTipsProperty: GraphProperty[java.lang.Boolean] =
    propertyGraph.property(RecentProjectsManagerBase.getInstanceEx.getRecentPaths.isEmpty)
  BindUtil.bindBooleanStorage(generateOnboardingTipsProperty,"NewProjectWizard.generateOnboardingTips")
  @TestOnly override private[project] def setGenerateOnboardingTips(value: java.lang.Boolean): Unit = generateOnboardingTipsProperty.set(value)
  private def needToGenerateOnboardingTips: Boolean = needToAddSampleCode && generateOnboardingTipsProperty.get()

  @TestOnly override def setScalaVersion(version: String): Unit = scalaVersionComboBox.setSelectedItemEnsuring(version)
  @TestOnly override private[project] def setSbtVersion(version: String): Unit = sbtVersionComboBox.setSelectedItemEnsuring(version)
  @TestOnly override private[project] def setPackagePrefix(prefix: String): Unit = packagePrefixTextField.setText(prefix)

  override def getSdk: Option[Sdk] = Option(sdkProperty.get())

  override protected val selections: SbtModuleBuilderSelections = SbtModuleBuilderSelections.default

  override protected lazy val defaultAvailableSbtVersions: Versions = Versions.SBT.allHardcodedVersions
  override protected lazy val defaultAvailableSbtVersionsForScala3: Versions = Versions.SBT.sbtVersionsForScala3(defaultAvailableSbtVersions)

  locally {
    getData.putUserData(SbtScalaNewProjectWizardData.KEY, this)
    getData.putUserData(ScalaSampleCodeNewProjectWizardData.KEY, this)
  }

  override def createBuilder(): ModuleBuilderBase[_] =
    new SbtModuleBuilder(this.selections)

  override protected def _addScalaSampleCode(project: Project, projectRoot: Path): Seq[VirtualFile] =
    addScalaSampleCode(
      project = project,
      path = s"$projectRoot/src/main/scala",
      isScala3 = this.selections.scalaVersion.exists(_.startsWith("3.")),
      packagePrefix = this.selections.packagePrefix,
      withOnboardingTips = needToGenerateOnboardingTips
    )


  override def setupUI(panel: Panel): Unit = {
    panel.row(JavaUiBundle.message("label.project.wizard.new.project.jdk"), (row: Row) => {
      val javaSdkFilter: kotlin.jvm.functions.Function1[SdkTypeId, java.lang.Boolean] =
        (it: SdkTypeId) => it.isInstanceOf[JavaSdkType] && !it.is[DependentSdkType]
      sdkComboBox = JdkComboBoxKt_Interop.sdkComboBox(row, getContext, sdkProperty, StdModuleTypes.JAVA.getId, javaSdkFilter, null, null, null, null)
      ComboBoxKt_Wrapper.columns(sdkComboBox, TextFieldKt.COLUMNS_MEDIUM)
      KUnit
    })

    panel.row(sbtLabelText, (row: Row) => {
      row.layout(RowLayout.PARENT_GRID)
      row.cell(sbtVersionComboBox).horizontalAlign(HorizontalAlign.FILL): @nowarn("cat=deprecation")
      row.cell(downloadSbtSourcesCheckbox)
      KUnit
    })

    setUpScalaUI(panel, downloadSourcesCheckbox = true)

    setupPackagePrefixUI(panel)

    setUpSampleCode(panel)

    panel.indent((p: Panel) => {
      p.row(null: JLabel, (row: Row) => {
        val cb = row.checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
        ButtonKt.bindSelected(cb, generateOnboardingTipsProperty: com.intellij.openapi.observable.properties.ObservableMutableProperty[java.lang.Boolean])
        ButtonKt.whenStateChangedFromUi(cb, null, value => {
          BSLog.logAddSampleCodeChanged(parent, value): @nowarn("cat=deprecation")
          KUnit
        })
        KUnit
      }).enabledIf(addSampleCodeProperty)
      KUnit
    })

    generateOnboardingTipsProperty.set(false)

    panel.collapsibleGroup(UIBundle.message("label.project.wizard.new.project.advanced.settings"), true, (panel: Panel) => {
      if (getContext.isCreatingNewProject) {
        panel.row(UIBundle.message("label.project.wizard.new.project.module.name"), (row: Row) => {
          val validator: kotlin.jvm.functions.Function2[ValidationInfoBuilder, JBTextField, ValidationInfo] = (builder, field) => {
            validateModuleName(builder, field)
          }
          TextFieldKt.bindText(row.textField, moduleNameProperty: com.intellij.openapi.observable.properties.ObservableMutableProperty[String])
            .horizontalAlign(HorizontalAlign.FILL)
            .validationOnInput(validator)
            .validationOnApply(validator): @nowarn("cat=deprecation")
          KUnit
        })
      }
      KUnit
    })

    initSelectionsAndUi(getContext.getDisposable)
  }

  private def validateModuleName(builder: ValidationInfoBuilder, field: JBTextField): ValidationInfo = {
    val moduleName = field.getText
    val project = getContext.getProject
    if (moduleName.isEmpty)
      builder.error(JavaUiBundle.message("module.name.location.dialog.message.enter.module.name"))
    else if (project == null)
      null
    else {
      // Name uniqueness
      val model = ProjectStructureConfigurable.getInstance(project)
        .nullSafe
        .map(_.getContext)
        .map(_.getModulesConfigurator)
        .map(_.getModuleModel)
        .orNull

      val module = if (model == null)
        ModuleManager.getInstance(project).findModuleByName(moduleName)
      else
        model.findModuleByName(moduleName)

      if (module != null)
        builder.error(JavaUiBundle.message("module.name.location.dialog.message.module.already.exist.in.project", moduleName))
      else
        null
    }
  }
}
