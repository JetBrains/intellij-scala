package org.jetbrains.sbt.project.template.wizard.buildSystem

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.{GraphProperty, ObservableProperty, PropertyGraph}
import com.intellij.openapi.observable.util.BindUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder._
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.ui.{DocumentAdapter, UIBundle}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ToNullSafe}
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.PackagePrefixStepLike
import org.jetbrains.plugins.scala.util.ui.extensions.JComboBoxOps
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.project.template.wizard.{SbtNewProjectWizardStep, ScalaNewProjectWizardMultiStep, ScalaVersionStepLike}
import org.jetbrains.sbt.project.template.{SbtModuleBuilder, SbtModuleBuilderSelections}

import java.util.concurrent.atomic.AtomicReference
import javax.swing.JLabel
import kotlin.Unit.{INSTANCE => KUnit}
import scala.annotation.nowarn
import scala.collection.immutable.ListSet

//noinspection ApiStatus,UnstableApiUsage
final class SbtScalaNewProjectWizardStep(parent: ScalaNewProjectWizardMultiStep)
  extends SbtNewProjectWizardStep(parent)
    with SbtScalaNewProjectWizardData
    with ScalaSampleCodeNewProjectWizardData
    with PackagePrefixStepLike
    with ScalaVersionStepLike {

  private val availableSbtVersions: AtomicReference[Option[Seq[SbtVersion]]] = new AtomicReference(None)
  private val availableSbtVersionsForScala3: AtomicReference[Option[Seq[SbtVersion]]] = new AtomicReference(None)

  override protected lazy val defaultAvailableScalaVersions: Seq[String] = Versions.Scala.allHardcodedVersions.map(_.presentation)

  @inline private def propertyGraph: PropertyGraph = getPropertyGraph

  private val moduleNameProperty: GraphProperty[String] = propertyGraph.lazyProperty(() => parent.getName)

  private val addSampleCodeProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.FALSE)
  BindUtil.bindBooleanStorage(addSampleCodeProperty, "NewProjectWizard.addSampleCodeState")
  private def needToAddSampleCode: Boolean = addSampleCodeProperty.get()

  @TestOnly override private[project] def setAddSampleCode(value: java.lang.Boolean): Unit = addSampleCodeProperty.set(value)

  @TestOnly override def setScalaVersion(version: String): Unit = scalaVersionComboBox.setSelectedItemEnsuring(version)
  @TestOnly override def setUseIndentationBasedSyntax(use: Boolean): Unit = setUseIndentationBasedSyntaxProperty(use)
  @TestOnly override private[project] def setSbtVersion(version: String): Unit = sbtVersionComboBox.setSelectedItemEnsuring(SbtVersion(version))
  @TestOnly override private[project] def setPackagePrefix(prefix: String): Unit = packagePrefixTextField.setText(prefix)

  private def getModuleName: String = moduleNameProperty.get()

  override protected val selections: SbtModuleBuilderSelections = SbtModuleBuilderSelections.default

  override protected val defaultAvailableSbtVersions: ListSet[SbtVersion] = ListSet(Versions.SBT.allHardcodedVersions.map(SbtVersion(_)): _*)
  private lazy val defaultAvailableSbtVersionsForScala3: Seq[SbtVersion] = Versions.SBT.sbtVersionsForScala3(defaultAvailableSbtVersions.toSeq)

  locally {
    moduleNameProperty.dependsOn(parent.getNameProperty: ObservableProperty[String], (() => parent.getName): kotlin.jvm.functions.Function0[_ <: String])

    getData.putUserData(SbtScalaNewProjectWizardData.KEY, this)
    getData.putUserData(ScalaSampleCodeNewProjectWizardData.KEY, this)
  }

  override def setupProject(project: Project): Unit = {
    val builder = new SbtModuleBuilder(this.selections)
    builder.setName(getModuleName)
    val projectRoot = getContext.getProjectDirectory.toAbsolutePath
    builder.setContentEntryPath(projectRoot.toString)

    setProjectOrModuleSdk(project, parent, builder, sdk)

    ExternalProjectsManagerImpl.setupCreatedProject(project)
    /** NEWLY_CREATED_PROJECT must be set up to prevent the call of markDirtyAllExternalProjects in ExternalProjectsDataStorage#load.
     * As a result, NEWLY_IMPORTED_PROJECT must also be set to keep the same behaviour as before in ExternalSystemStartupActivity.kt:48 (do not call ExternalSystemUtil#refreshProjects).
     * Similar thing is done in AbstractGradleModuleBuilder#setupModule */
    project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, java.lang.Boolean.TRUE)
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, java.lang.Boolean.TRUE)

    setupUseIndentationBasedSyntaxInProject(project)

    if (needToAddSampleCode) {
      val files = addScalaSampleCode(
        project = project,
        path = s"$projectRoot/src/main/scala",
        isScala3 = this.selections.scalaVersion.exists(_.startsWith("3.")),
        packagePrefix = this.selections.packagePrefix,
        withOnboardingTips = true
      )
      builder.openFileEditorAfterProjectOpened = files
    }

    startJdkDownloadIfNeeded(project)
    builder.commit(project)
  }

  override def setupUI(panel: Panel): Unit = {
    setupJavaSdkUI(panel)

    setupSbtUI(panel)

    setUpScalaUI(panel, downloadSourcesCheckbox = true)

    setupPackagePrefixUI(panel)

    panel.row(null: JLabel, (row: Row) => {
      val cb = row.checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
      ButtonKt.bindSelected(cb, addSampleCodeProperty: com.intellij.openapi.observable.properties.ObservableMutableProperty[java.lang.Boolean])
      ButtonKt.whenStateChangedFromUi(cb, null, value => {
        NewProjectWizardCollector.Base.INSTANCE.logAddSampleCodeChanged(parent, value)
        KUnit
      })
      KUnit
    }).topGap(TopGap.SMALL)

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

  override def setSbtVersion(versions: Seq[SbtVersion]): Unit = {
    availableSbtVersions.set(versions.toOption)
    availableSbtVersionsForScala3.set(Versions.SBT.sbtVersionsForScala3(versions).toOption)
    updateSelectionsAndElementsModelForSbt(versions)
  }

  override def loadSbtVersions(indicator: ProgressIndicator): Seq[SbtVersion] =
    Versions.SBT.loadVersionsWithProgress(indicator).map(SbtVersion(_))

  /**
   * Initializes selections and UI elements only once
   */
  override protected def initSelectionsAndUi(contextDisposable: Disposable): Unit = {
    super.initSelectionsAndUi(contextDisposable)
    _initSelectionsAndUi
    if (!isUnitTestMode) {
      downloadSbtVersions(contextDisposable)
    }
  }

  private lazy val _initSelectionsAndUi: Unit = {
    selections.updateSbtVersion(defaultAvailableSbtVersions.toSeq)

    initUiElementsModel()
    initUiElementsListeners()
  }

  private def updateSelectionsAndElementsModelForSbt(sbtVersions: Seq[SbtVersion]): Unit = {
    if (!isSbtVersionManuallySelected.get()) {
      selections.sbtVersion = None
      selections.updateSbtVersion(sbtVersions)
    }
    sbtVersionComboBox.updateComboBoxModel(sbtVersions.toArray, selections.sbtVersion)
  }

  private def initUiElementsModel(): Unit = {
    initUiElementsModelFrom(selections)
    updateSupportedSbtVersionsForSelectedScalaVersion(selections.scalaVersion)
  }

  private def initUiElementsModelFrom(selections: SbtModuleBuilderSelections): Unit = {
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    downloadSbtSourcesCheckbox.setSelected(selections.downloadSbtSources)
    packagePrefixTextField.setText(selections.packagePrefix.getOrElse(""))
  }

  /**
   * Init UI --> Selections binding
   */
  private def initUiElementsListeners(): Unit = {
    sbtVersionComboBox.addActionListener { _ =>
      selections.sbtVersion = sbtVersionComboBox.getSelectedItemTyped
    }

    scalaVersionComboBox.addActionListener { _ =>
      // note: the scalaVersionComboBox item must be passed on instead of simply selections.scalaVersion
      // because it may happen that the listener added to the scalaVersionComboBox in ScalaVersionStepLike will be called after this, and
      // at this stage an outdated value can be stored in selections.scalaVersion
      updateSupportedSbtVersionsForSelectedScalaVersion(scalaVersionComboBox.getSelectedItemTyped)
    }

    downloadSbtSourcesCheckbox.addChangeListener { _ =>
      selections.downloadSbtSources = downloadSbtSourcesCheckbox.isSelected
    }

    packagePrefixTextField.getDocument.addDocumentListener(
      (_ => selections.packagePrefix = Option(packagePrefixTextField.getText).filter(_.nonEmpty)): DocumentAdapter
    )
  }

  private def isScala3Version(scalaVersion: String): Boolean =
    scalaVersion.startsWith("3")

  /**
   * Ensure that we do not show sbt versions < 1.5 if Scala 3.X is selected
   */
  private def updateSupportedSbtVersionsForSelectedScalaVersion(scalaVersion: Option[String]): Unit = {
    val sbtVersions = availableSbtVersions.get().getOrElse(defaultAvailableSbtVersions)
    val sbtVersionsForScala3 = availableSbtVersionsForScala3.get().getOrElse(defaultAvailableSbtVersionsForScala3)
    val isScala3Selected = scalaVersion.exists(isScala3Version)
    val supportedSbtVersions = if (isScala3Selected) sbtVersionsForScala3 else sbtVersions
    sbtVersionComboBox.setItems(supportedSbtVersions.toArray)

    // if we select Scala3 version but had Scala2 version selected before and some sbt version incompatible with Scala3,
    // the latest item from the list will be automatically selected
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    selections.updateSbtVersion(sbtVersions.toSeq)
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
