package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.{ProjectWizardJdkComboBox, ProjectWizardJdkComboBoxKt, ProjectWizardJdkIntent}
import com.intellij.ide.wizard.{AbstractNewProjectWizardStep, NewProjectWizardStep}
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.{GraphProperty, PropertyGraph}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.projectRoots.{JavaSdkVersion, Sdk}
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.{DialogValidationRequestor, RequestorsKt}
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.{BottomGap, ComboBoxKt, Panel, Row, RowLayout}
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.util.AsynchronousVersionsDownloading
import org.jetbrains.sbt.project.template.SComboBox
import org.jetbrains.sbt.project.template.wizard.ScalaVersionStepLike.ScalaJdkValidationContext
import org.jetbrains.sbt.project.template.wizard.kotlin_interop.KotlinInteropUtils
import org.jetbrains.sbt.{SbtBundle, SbtVersion}

import java.lang
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Unit.INSTANCE as KUnit
import java.util.{List => JList}
import kotlin.jvm.functions
import scala.annotation.nowarn
import scala.collection.immutable.ListSet
import scala.jdk.CollectionConverters.*

abstract class SbtNewProjectWizardStep(parent: NewProjectWizardStep)
  extends AbstractNewProjectWizardStep(parent)
  with AsynchronousVersionsDownloading
  with ScalaVersionStepLike {

  protected val defaultAvailableSbtVersions: ListSet[SbtVersion]

  @inline private def propertyGraph: PropertyGraph = getPropertyGraph

  protected val jdkIntentProperty: GraphProperty[ProjectWizardJdkIntent] = propertyGraph.property(ProjectWizardJdkIntent.NoJdk.INSTANCE)

  protected lazy val sbtVersionProperty: GraphProperty[SbtVersion] = propertyGraph.property(defaultAvailableSbtVersions.head)
  protected val downloadSbtSourcesProperty: GraphProperty[lang.Boolean] = propertyGraph.property(java.lang.Boolean.FALSE)

  protected def jdkIntent: Option[ProjectWizardJdkIntent] = Option(jdkIntentProperty.get())

  protected final val isSbtVersionManuallySelected: AtomicBoolean = new AtomicBoolean(false)
  /**
   * Prevents auto-adjusting the JDK if the user explicitly picked some JDK when Scala 3.8+ is selected.
   * Resets when switching to an older Scala version.
   */
  private val hasUserSelectedJdkForScala38Plus = new AtomicBoolean(false)
  private val isSbtLoading = new AtomicBoolean(false)

  protected lazy val sbtVersionComboBox: SComboBox[SbtVersion] = createSComboBoxWithSearchingListRenderer(defaultAvailableSbtVersions, None, isSbtLoading)

  protected def loadSbtVersions(indicator: ProgressIndicator): Seq[SbtVersion]
  protected def setDownloadedSbtVersions(versions: Seq[SbtVersion]): Unit

  protected val downloadSbtSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.sbt.sources"))
  )

  private var jdkComboBox: ProjectWizardJdkComboBox = scala.compiletime.uninitialized

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
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(jdkIntentProperty))
        .validationOnInput(() => sbtWithJdkValidation())
      val downloadSbtSourcesCheckboxCell = row.cell(downloadSbtSourcesCheckbox)

      KotlinInteropUtils.bindItem(sbtVersionComboBoxCell, sbtVersionProperty)
      KotlinInteropUtils.bind(downloadSbtSourcesCheckboxCell, downloadSbtSourcesProperty)

      KUnit
    })

  protected def setUpScalaUIWithJDKValidation(panel: Panel): Unit = {
    setUpScalaUI(
      panel,
      downloadSourcesCheckbox = true,
      jdkValidationCtx = Some(ScalaJdkValidationContext(jdkIntentProperty, () => getExpectedJavaSdkVersion))
    )

    // Auto-select JDK 17+ for Scala 3.8+ if the current JDK is < 17 or "No JDK" is selected
    scalaVersionComboBox.addActionListener { _ =>
      getScalaVersionIfAtLeast3_8 match
        case Some(_) =>
          val minJdk = 17
          val isJdkIncompatible = getExpectedJavaSdkVersion.exists(_.getMaxLanguageLevel.feature < minJdk) || isNoJdkSelected

          val needJdkAdjust = isJdkIncompatible && !hasUserSelectedJdkForScala38Plus.get()
          if (needJdkAdjust) {
            def findCompatibleJdk[T <: ProjectWizardJdkIntent](jdks: JList[T]): Option[T] =
              jdks.asScala
                .filter(_.getJavaVersion.feature >= minJdk)
                .maxByOption(_.getJavaVersion.feature)

            val suitableJdk = findCompatibleJdk(jdkComboBox.getRegistered).orElse(findCompatibleJdk(jdkComboBox.getDetectedJDKs))
            suitableJdk.foreach(jdkIntentProperty.set)
          }
        case _ =>
          // For Scala < 3.8, clear the tracking flag to allow re-adjusting the JDK if Scala 3.8+ is selected later.
          hasUserSelectedJdkForScala38Plus.set(false)
    }
  }

  protected def setupJavaSdkUI(builder: Panel): Unit = {
    builder.row(JavaUiBundle.message("label.project.wizard.new.project.jdk"), (row: Row) => {
      val jdkComboBoxCell = ProjectWizardJdkComboBoxKt.projectWizardJdkComboBox(
        this,
        row,
        jdkIntentProperty,
        { (_: Sdk) => lang.Boolean.TRUE },
        (javaVersion: JavaVersion, _: String) =>
          val validationErrors = Seq(
            jdkWithSbtValidation(javaVersion),
            jdkWithScalaValidation(javaVersion)
          ).filter(_ != null)

          if (validationErrors.isEmpty) null
          else validationErrors.mkString("<html>", "<br>", "</html>")
      )
      jdkComboBoxCell
        .validationRequestor(new DialogValidationRequestor() {
          // Initiate one-time validation when the build system panel is shown
          override def subscribe(disposable: Disposable, validate: functions.Function0[kotlin.Unit]): Unit =
            validate.invoke()
        })
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(sbtVersionProperty))
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(scalaVersionProperty))
        .validationRequestor(RequestorsKt.getWHEN_PROPERTY_CHANGED.invoke(jdkIntentProperty))

      ComboBoxKt.whenItemChangedFromUi(jdkComboBoxCell, null, _ =>
        getScalaVersionIfAtLeast3_8.foreach { _ =>
          hasUserSelectedJdkForScala38Plus.set(true)
        }
        KUnit
      )
      jdkComboBox = jdkComboBoxCell.getComponent

      KUnit
    }).bottomGap(BottomGap.SMALL)
  }

  @Nullable
  private def jdkWithSbtValidation(javaVersion: JavaVersion): String = {
    if (javaVersion == null) return null
    val sbtVersion = sbtVersionProperty.get()
    val highestCompatibleJdk = JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt(javaVersion, sbtVersion)
    highestCompatibleJdk.map { version =>
      SbtBundle.message("sbt.incompatible.versions.message", sbtVersion.minor, version.toFeatureString)
    }.orNull
  }

  @Nullable
  private def jdkWithScalaValidation(javaVersion: JavaVersion): String = {
    if (javaVersion == null) return null
    val scalaVersion = getScalaVersion match {
      case Some(version) => version
      case None => return null
    }
    val minRequiredJdk = JdkScalaCompatibilityChecker.getMinimumJdkRequiredForScala(javaVersion, scalaVersion)
    minRequiredJdk match {
      case Some(jdk) =>
        SbtBundle.message("scala.incompatible.versions.jdk.too.low.message", scalaVersion.minor, jdk.toFeatureString)
      case None =>
        val highestCompatibleJdk = JdkScalaCompatibilityChecker.getHighestCompatibleJdkForScala(javaVersion, scalaVersion)
        highestCompatibleJdk.map { version =>
          SbtBundle.message("scala.incompatible.versions.message", scalaVersion.minor, version.toFeatureString)
        }.orNull
    }
  }

  @Nullable
  private def sbtWithJdkValidation(): ValidationInfo = {
    val jdkVersion = getExpectedJavaSdkVersion.orNull
    if (jdkVersion == null) return null
    val sbtVersion = sbtVersionProperty.get()
    val javaVersion = JavaVersion.compose(jdkVersion.getMaxLanguageLevel.feature())
    val minimumCompatibleSbt = JdkSbtCompatibilityChecker.getMinimumSbtToJdkCompatibleVersion(javaVersion, sbtVersion)
    minimumCompatibleSbt.map { version =>
      new ValidationInfo(SbtBundle.message("jdk.sbt.incompatible.versions.message", javaVersion.feature, version.minor), sbtVersionComboBox).asWarning()
    }.orNull
  }

  protected def getExpectedJavaSdkVersion: Option[JavaSdkVersion] =
    for {
      intent <- jdkIntent
      versionString <- Option(intent.getVersionString)
      javaSdkVersion <- Option(JavaSdkVersion.fromVersionString(versionString))
    } yield javaSdkVersion

  private def isNoJdkSelected: Boolean =
    jdkIntent.contains(ProjectWizardJdkIntent.NoJdk.INSTANCE)

  protected final def downloadSbtVersions(disposable: Disposable): Unit = {
    val sbtDownloadVersions: ProgressIndicator => Seq[SbtVersion] = indicator => loadSbtVersions(indicator)
    downloadVersionsAsynchronously(isSbtLoading, disposable, sbtDownloadVersions, Versions.SBT.toString) { versions =>
      setDownloadedSbtVersions(versions)
    }

    sbtVersionComboBox.addActionListener { _ =>
      isSbtVersionManuallySelected.set(true)
    }
  }
}
