package org.jetbrains.sbt.project.template.wizard

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.DocumentAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.{PackagePrefixStepLike, ScalaVersionDownloadingDialog}
import org.jetbrains.plugins.scala.util.AsynchronousVersionsDownloading
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.{SComboBox, SbtModuleBuilderSelections}

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.collection.immutable.ListSet

private[template] trait SbtModuleStepLike extends PackagePrefixStepLike with AsynchronousVersionsDownloading {

  protected def selections: SbtModuleBuilderSelections

  protected val defaultAvailableScalaVersions: Versions
  protected val defaultAvailableSbtVersions: Versions
  protected val defaultAvailableSbtVersionsForScala3: Versions

  private val availableSbtVersions: AtomicReference[Option[Versions]] = new AtomicReference(None)
  private val availableSbtVersionsForScala3: AtomicReference[Option[Versions]] = new AtomicReference(None)

  private val isSbtVersionManuallySelected: AtomicBoolean = new AtomicBoolean(false)
  private val isScalaVersionManuallySelected: AtomicBoolean = new AtomicBoolean(false)

  //
  // Raw UI elements
  //

  private val isSbtLoading = new AtomicBoolean(false)
  private val isScalaLoading = new AtomicBoolean(false)
  protected lazy val sbtVersionComboBox: SComboBox[String] = createSComboBoxWithSearchingListRenderer(ListSet(defaultAvailableSbtVersions.versions: _*), None, isSbtLoading)
  protected lazy val scalaVersionComboBox: SComboBox[String] = createSComboBoxWithSearchingListRenderer(ListSet(defaultAvailableScalaVersions.versions: _*), None, isScalaLoading)

  private def downloadAvailableVersions(disposable: Disposable): Unit = {
    val sbtIndicator = new EmptyProgressIndicator
    val scalaIndicator = new EmptyProgressIndicator
    Disposer.register(disposable, () => {
      Seq(sbtIndicator, scalaIndicator).foreach(_.cancel)
    })

    val sbtDownloadVersions: () => Versions = () => Versions.SBT.loadVersionsWithProgress(sbtIndicator)
    downloadVersionsAsynchronously(isSbtLoading, sbtIndicator, sbtDownloadVersions, Versions.SBT.toString) { v =>
      availableSbtVersions.set(v.toOption)
      availableSbtVersionsForScala3.set(Versions.SBT.sbtVersionsForScala3(v).toOption)
      updateSelectionsAndElementsModelForSbt(v)
    }

    val scalaDownloadVersions: () => Versions = () => Versions.Scala.loadVersionsWithProgress(scalaIndicator)
    downloadVersionsAsynchronously(isScalaLoading, scalaIndicator, scalaDownloadVersions, Versions.Scala.toString) { v =>
      updateSelectionsAndElementsModelForScala(v)
    }
  }

  protected val sbtLabelText: String = SbtBundle.message("sbt.settings.sbt")
  protected val scalaLabelText: String = SbtBundle.message("sbt.settings.scala")

  protected val downloadSbtSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.sbt.sources"))
  )
  protected val downloadScalaSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.scala.standard.library.sources"))
  )

  /**
   * Initializes selections and UI elements only once
   */
  protected def initSelectionsAndUi(contextDisposable: Disposable): Unit = {
    _initSelectionsAndUi
    downloadAvailableVersions(contextDisposable)
  }
  private lazy val _initSelectionsAndUi: Unit = {
    selections.update(Versions.SBT, defaultAvailableSbtVersions)
    selections.update(Versions.Scala, defaultAvailableScalaVersions)

    initUiElementsModel()
    initUiElementsListeners()
  }

  private def updateSelectionsAndElementsModelForSbt(sbtVersions: Versions): Unit = {
    if (!isSbtVersionManuallySelected.get()) {
      selections.sbtVersion = None
      selections.update(Versions.SBT, sbtVersions)
    }
    sbtVersionComboBox.updateComboBoxModel(sbtVersions.versions.toArray, selections.sbtVersion)
  }

  private def updateSelectionsAndElementsModelForScala(scalaVersions: Versions): Unit = {
    if (!isScalaVersionManuallySelected.get()) {
      selections.scalaVersion = None
      selections.update(Versions.Scala, scalaVersions)
    }
    scalaVersionComboBox.updateComboBoxModel(scalaVersions.versions.toArray, selections.scalaVersion)
    initSelectedScalaVersion(scalaVersions)
  }

  private def initUiElementsModel(): Unit = {
    initUiElementsModelFrom(selections)
    initSelectedScalaVersion(defaultAvailableScalaVersions)
    updateSupportedSbtVersionsForSelectedScalaVersion()
  }

  private def initUiElementsModelFrom(selections: SbtModuleBuilderSelections): Unit = {
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    scalaVersionComboBox.setSelectedItemSafe(selections.scalaVersion.orNull)
    downloadSbtSourcesCheckbox.setSelected(selections.downloadSbtSources)
    downloadScalaSourcesCheckbox.setSelected(selections.downloadScalaSdkSources)
    packagePrefixTextField.setText(selections.packagePrefix.getOrElse(""))
  }

  /**
   * Init UI --> Selections binding
   */
  private def initUiElementsListeners(): Unit = {
    sbtVersionComboBox.addActionListener { _ =>
      isSbtVersionManuallySelected.set(true)
      selections.sbtVersion = sbtVersionComboBox.getSelectedItemTyped
    }
    scalaVersionComboBox.addActionListener { _ =>
      isScalaVersionManuallySelected.set(true)
      selections.scalaVersion = scalaVersionComboBox.getSelectedItemTyped

      updateSupportedSbtVersionsForSelectedScalaVersion()
    }

    downloadScalaSourcesCheckbox.addChangeListener(_ =>
      selections.downloadScalaSdkSources = downloadScalaSourcesCheckbox.isSelected
    )
    downloadSbtSourcesCheckbox.addChangeListener { _ =>
      selections.downloadSbtSources = downloadSbtSourcesCheckbox.isSelected
    }

    packagePrefixTextField.getDocument.addDocumentListener(
      (_ => selections.packagePrefix = Option(packagePrefixTextField.getText).filter(_.nonEmpty)): DocumentAdapter
    )
  }

  private def isScala3Version(scalaVersion: String): Boolean =
    scalaVersion.startsWith("3")

  private def initSelectedScalaVersion(scalaVersions: Versions): Unit = {
    selections.scalaVersion match {
      case Some(version) if scalaVersions.versions.contains(version) =>
        scalaVersionComboBox.setSelectedItemSafe(version)

        if (selections.scrollScalaVersionDropdownToTheTop) {
          ScalaVersionDownloadingDialog.UiUtils.scrollToTheTop(scalaVersionComboBox)
        }
      case _ if scalaVersionComboBox.getItemCount > 0 =>
        scalaVersionComboBox.setSelectedIndex(0)
      case _ =>
    }
  }

  /**
   * Ensure that we do not show sbt versions < 1.5 if Scala 3.X is selected
   */
  private def updateSupportedSbtVersionsForSelectedScalaVersion(): Unit = {
    val sbtVersions = availableSbtVersions.get().getOrElse(defaultAvailableSbtVersions)
    val sbtVersionsForScala3 = availableSbtVersionsForScala3.get().getOrElse(defaultAvailableSbtVersionsForScala3)
    val isScala3Selected = selections.scalaVersion.exists(isScala3Version)
    val supportedSbtVersions = if (isScala3Selected) sbtVersionsForScala3 else sbtVersions
    sbtVersionComboBox.setItems(supportedSbtVersions.versions.toArray)

    // if we select Scala3 version but had Scala2 version selected before and some sbt version incompatible with Scala3,
    // the latest item from the list will be automatically selected
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    selections.update(Versions.SBT, sbtVersions)
  }
}