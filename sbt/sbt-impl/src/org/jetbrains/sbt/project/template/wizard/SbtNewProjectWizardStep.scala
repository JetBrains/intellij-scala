package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.DetectedJdk
import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.ide.projectWizard.{ProjectWizardJdkComboBox, ProjectWizardJdkComboBoxKt}
import com.intellij.ide.wizard.NewProjectWizardBaseData.getBaseData
import com.intellij.ide.wizard.{AbstractNewProjectWizardStep, NewProjectWizardStep}
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.{GraphProperty, PropertyGraph}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.{JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.{DialogValidationRequestor, RequestorsKt}
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.{BottomGap, Panel, Row, RowLayout}
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.util.AsynchronousVersionsDownloading
import org.jetbrains.sbt.project.template.SComboBox
import org.jetbrains.sbt.project.template.wizard.kotlin_interop.KotlinInteropUtils
import org.jetbrains.sbt.{SbtBundle, SbtVersion}

import java.lang
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Unit.{INSTANCE => KUnit}
import kotlin.jvm.functions
import scala.annotation.nowarn
import scala.collection.immutable.ListSet

//TODO Add a Scala combobox (and all related functionality, such as downloading) to this step so that everything needed
// for sbt is in one place.
abstract class SbtNewProjectWizardStep(parent: NewProjectWizardStep) extends AbstractNewProjectWizardStep(parent)
  with AsynchronousVersionsDownloading {

  protected val defaultAvailableSbtVersions: ListSet[SbtVersion]

  @inline private def propertyGraph: PropertyGraph = getPropertyGraph

  protected val sdkProperty: GraphProperty[Sdk] = propertyGraph.property(null)
  private val sdkDownloadTaskProperty: GraphProperty[SdkDownloadTask] = propertyGraph.property[SdkDownloadTask](null)

  protected lazy val sbtVersionProperty: GraphProperty[SbtVersion] = propertyGraph.property(defaultAvailableSbtVersions.head)
  protected val downloadSbtSourcesProperty: GraphProperty[lang.Boolean] = propertyGraph.property(java.lang.Boolean.FALSE)

  private def sdkDownloadTask: Option[SdkDownloadTask] = Option(sdkDownloadTaskProperty.get())
  protected def sdk: Option[Sdk] = Option(sdkProperty.get())

  protected final val isSbtVersionManuallySelected: AtomicBoolean = new AtomicBoolean(false)
  private val isSbtLoading = new AtomicBoolean(false)

  protected lazy val sbtVersionComboBox: SComboBox[SbtVersion] = createSComboBoxWithSearchingListRenderer(defaultAvailableSbtVersions, None, isSbtLoading)

  protected def loadSbtVersions(indicator: ProgressIndicator): Seq[SbtVersion]
  protected def setSbtVersion(versions: Seq[SbtVersion]): Unit

  protected val downloadSbtSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.sbt.sources"))
  )

  private var jdkComboBox: ProjectWizardJdkComboBox = _

  protected def setupSbtUI(panel: Panel): Unit =
    panel.row(SbtBundle.message("sbt.settings.sbt"), (row: Row) => {
      row.layout(RowLayout.PARENT_GRID)

      val sbtVersionComboBoxCell = row.cell(sbtVersionComboBox).horizontalAlign(HorizontalAlign.FILL): @nowarn("cat=deprecation")
      sbtVersionComboBoxCell
        .validationRequestor(new DialogValidationRequestor() {
          // Initiate one-time validation when the build system panel is shown
          override def subscribe(disposable: Disposable, validate: functions.Function0[kotlin.Unit]): Unit =
            validate.invoke()
        })
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(sbtVersionProperty))
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(sdkProperty))
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(sdkDownloadTaskProperty))
        .validationOnInput(() => sbtWithJdkValidation())
      val downloadSbtSourcesCheckboxCell = row.cell(downloadSbtSourcesCheckbox)

      KotlinInteropUtils.bindItem(sbtVersionComboBoxCell, sbtVersionProperty)
      KotlinInteropUtils.bind(downloadSbtSourcesCheckboxCell, downloadSbtSourcesProperty)

      KUnit
    })

  protected def startJdkDownloadIfNeeded(project: Project): Unit =
    sdkDownloadTask.collect { case task: JdkDownloadTask =>
      val service = project.getService(classOf[JdkDownloadService])
      service.scheduleDownloadJdkForNewProject(task)
    }

  protected def setupJavaSdkUI(builder: Panel): Unit = {
    builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk"), (row: Row) => {
      // TODO If a ProjectWizardJdkPredicate parameter is added to the Kotlin extension function `#projectWizardJdkComboBox`,
      //  make use of it, as it handles many parameters that are currently duplicated here.
      val jdkComboBoxCell = ProjectWizardJdkComboBoxKt.projectWizardJdkComboBox(
        row,
        sdkProperty,
        sdkDownloadTaskProperty,
        getBaseData(this).getPathProperty,
        { s: Sdk =>
          getContext.setProjectJdk(s)
          KUnit
        },
        getContext.getDisposable,
        getContext.getProjectJdk,
        { _: Sdk => { lang.Boolean.TRUE }},
        (javaVersion: JavaVersion, _: String) => jdkWithSbtValidation(javaVersion)
      )
      jdkComboBoxCell
        .validationRequestor(new DialogValidationRequestor() {
          // Initiate one-time validation when the build system panel is shown
          override def subscribe(disposable: Disposable, validate: functions.Function0[kotlin.Unit]): Unit =
            validate.invoke()
        })
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(sbtVersionProperty))
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(sdkProperty))
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(sdkDownloadTaskProperty))

      jdkComboBox = jdkComboBoxCell.getComponent

      KUnit
    }).bottomGap(BottomGap.SMALL)
  }

  @Nullable
  private def jdkWithSbtValidation(javaVersion: JavaVersion): String = {
    if (javaVersion == null) return null
    val sbtVersion = sbtVersionProperty.get()
    val minimumCompatibleSbt = JdkSbtCompatibilityChecker.getMinimumSbtToJdkCompatibleVersion(javaVersion, sbtVersion)
    minimumCompatibleSbt.map { version =>
      SbtBundle.message("jdk.sbt.incompatible.versions.message", javaVersion.feature, version.minor)
    }.orNull
  }

  @Nullable
  private def sbtWithJdkValidation(): ValidationInfo = {
    val jdkVersion = getExpectedJavaSdkVersion.orNull
    if (jdkVersion == null) return null
    val sbtVersion = sbtVersionProperty.get()
    val javaVersion = JavaVersion.compose(jdkVersion.getMaxLanguageLevel.feature())
    val highestCompatibleJdk = JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt(javaVersion, sbtVersion)
    highestCompatibleJdk.map { version =>
      new ValidationInfo(SbtBundle.message("sbt.incompatible.versions.message", sbtVersion.minor, version.toFeatureString), sbtVersionComboBox).asWarning()
    }.orNull
  }

  protected def getExpectedJavaSdkVersion: Option[JavaSdkVersion] = {
    val versionString = sdk.map(_.getVersionString)
    val plannedVersion = sdkDownloadTask.map(_.getPlannedVersion)
    val expectedJdkVersion = versionString
      .orElse(getDetectedJdkIfAny)
      .orElse(plannedVersion)
    expectedJdkVersion.map(JavaSdkVersion.fromVersionString)
  }

  /**
   * It's a workaround for <a href="https://youtrack.jetbrains.com/issue/IDEA-368023/The-sdkProperty-is-null-when-JDK-from-the-detected-JDKs-is-chosen-rather-than-from-the-registered-ones">IDEA-368023</a>.
   * Remove when it's fixed
   */
  private def getDetectedJdkIfAny: Option[String] = {
    val selectedJdk = Option(jdkComboBox).flatMap(cb => Option(cb.getSelectedItem))
    selectedJdk.collect { case x: DetectedJdk => x.getVersion }
  }

  protected final def downloadSbtVersions(disposable: Disposable): Unit = {
    val sbtDownloadVersions: ProgressIndicator => Seq[SbtVersion] = indicator => loadSbtVersions(indicator)
    downloadVersionsAsynchronously(isSbtLoading, disposable, sbtDownloadVersions, Versions.SBT.toString)(setSbtVersion)

    sbtVersionComboBox.addActionListener { _ =>
      isSbtVersionManuallySelected.set(true)
    }
  }
}
