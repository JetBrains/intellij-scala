package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.{ProjectWizardJdkComboBox, ProjectWizardJdkComboBoxKt, ProjectWizardJdkIntent}
import com.intellij.ide.wizard.{AbstractNewProjectWizardStep, NewProjectWizardStep}
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.{GraphProperty, PropertyGraph}
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator}
import com.intellij.openapi.projectRoots.{JavaSdkVersion, Sdk}
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.{DialogValidationRequestor, RequestorsKt}
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.{BottomGap, Panel, Row, RowLayout}
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
import java.util.concurrent.ExecutionException
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

  private val isSbtLoading = new AtomicBoolean(false)

  protected lazy val sbtVersionComboBox: SComboBox[SbtVersion] = createSComboBoxWithSearchingListRenderer(defaultAvailableSbtVersions, None, isSbtLoading)

  @throws[ExecutionException]
  @throws[InterruptedException]
  @throws[ProcessCanceledException]
  protected def loadSbtVersions(indicator: ProgressIndicator): Seq[SbtVersion]
  protected def setDownloadedSbtVersions(versions: Seq[SbtVersion]): Unit

  protected val downloadSbtSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.sbt.sources"))
  )

  private var jdkComboBox: ProjectWizardJdkComboBox = scala.compiletime.uninitialized

  protected def setupSbtUI(panel: Panel): Unit = {
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

    sbtVersionComboBox.addActionListener { _ =>
      if isJdkBelow(minJdkFeatureForSbt) then autoSelectJdkIfNeeded()
    }
  }

  private def minJdkFeatureForScala: Int =
    getScalaVersion.flatMap(JdkScalaCompatibilityChecker.getMinimumJdkVersionForScala).map(_.feature).getOrElse(0)

  private def minJdkFeatureForSbt: Int =
    JdkSbtCompatibilityChecker.getMinimumJdkVersionForSbt(sbtVersionProperty.get()).map(_.feature).getOrElse(0)

  private def isJdkBelow(minJdkFeature: Int): Boolean = {
    val jdkFeature = getExpectedJavaSdkVersion.map(_.getMaxLanguageLevel.feature).getOrElse(0)
    jdkFeature < minJdkFeature || isNoJdkSelected
  }

  protected def setUpScalaUIWithJDKValidation(panel: Panel): Unit = {
    setUpScalaUI(
      panel,
      downloadSourcesCheckbox = true,
      jdkValidationCtx = Some(ScalaJdkValidationContext(jdkIntentProperty, () => getExpectedJavaSdkVersion))
    )

    scalaVersionComboBox.addActionListener { _ =>
      if isJdkBelow(minJdkFeatureForScala) then autoSelectJdkIfNeeded()
    }
  }

  /**
   * Auto-selects a compatible JDK when the current one does not meet the minimum requirement for the selected Scala/sbt versions.
   * In addition to the lower bound, it also respects the upper bound — the highest JDK compatible with both Scala and sbt.
   * This is important, because when e.g., the user changes sbt to 2.0.0-RC9 while Scala 2.13.10 and JDK 11 are selected,
   * we cannot simply pick the highest available JDK, because Scala 2.13.10 only supports JDK < 21.
   * In this case, the selected JDK must satisfy both the sbt minimum (JDK 17+) and the Scala upper bound (JDK < 21).
   */
  private def autoSelectJdkIfNeeded(): Unit = {
    // Take the higher of the two minimum constraints to satisfy both Scala and sbt requirements.
    // Currently, the only constraint is JDK 17 for Scala 3.8+ or sbt 2.0.0-RC9+, so `requiredMinJdk` is effectively
    // either 0 or 17, but it will be useful in the future.
    val requiredMinJdk = minJdkFeatureForScala max minJdkFeatureForSbt
    if (requiredMinJdk > 0 && isJdkBelow(requiredMinJdk)) {
      // It's explained in the Scaladoc why this is needed
      val maxJdkForSbt = JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt(sbtVersionProperty.get())
      val maxJdkForScala = getScalaVersion.flatMap(JdkScalaCompatibilityChecker.getHighestCompatibleJdkForScala)
      val requiredMaxJdk = (maxJdkForSbt ++ maxJdkForScala).minOption

      def selectJdk[T <: ProjectWizardJdkIntent](jdks: JList[T]): Option[T] = {
        val jdksAboveMin = jdks.asScala.filter(_.getJavaVersion.feature >= requiredMinJdk)
        val (withinRange, outsideRange) = jdksAboveMin.partition(jdk => requiredMaxJdk.forall(_.feature >= jdk.getJavaVersion.feature))
        // Prefer the highest JDK within range, since a newer JDK is always recommended.
        // If no JDK satisfies both scala and sbt constraints (all JDKs from `jdksAboveMin` exceed `requiredMaxJdk`), fall back to the lowest JDK
        // from `jdksAboveMin` to avoid proposing an unnecessarily extremely high version (e.g., JDK 25 for sbt 1.5).
        withinRange.maxByOption(_.getJavaVersion.feature)
          .orElse(outsideRange.minByOption(_.getJavaVersion.feature))
      }

      val suitableJdk = selectJdk(jdkComboBox.getRegistered).orElse(selectJdk(jdkComboBox.getDetectedJDKs))
      suitableJdk.foreach(jdkIntentProperty.set)
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

      jdkComboBox = jdkComboBoxCell.getComponent

      KUnit
    }).bottomGap(BottomGap.SMALL)
  }

  @Nullable
  private def jdkWithSbtValidation(javaVersion: JavaVersion): String = {
    if (javaVersion == null) return null
    val sbtVersion = sbtVersionProperty.get()
    val minimumRequiredJdk = JdkSbtCompatibilityChecker.getMinimumJdkToSbtCompatibleVersion(javaVersion, sbtVersion)
    minimumRequiredJdk match {
      case Some(minJdk) =>
        SbtBundle.message("sbt.incompatible.versions.jdk.too.low.message", sbtVersion.minor, minJdk.toFeatureString)
      case None =>
        val highestCompatibleJdk = JdkSbtCompatibilityChecker.getHighestCompatibleJdkForSbt(javaVersion, sbtVersion)
        highestCompatibleJdk.map { version =>
          SbtBundle.message("sbt.incompatible.versions.message", sbtVersion.minor, version.toFeatureString)
        }.orNull
    }
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
