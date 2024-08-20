package org.jetbrains.sbt.project.template.wizard

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.DocumentAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.PackagePrefixStepLike
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.{SComboBox, SbtModuleBuilderSelections}

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.collection.immutable.ListSet

private[template] trait SbtModuleStepLike extends PackagePrefixStepLike with ScalaVersionStepLike {

  override protected def selections: SbtModuleBuilderSelections

  protected val defaultAvailableSbtVersions: Versions
  protected val defaultAvailableSbtVersionsForScala3: Versions

  private val availableSbtVersions: AtomicReference[Option[Versions]] = new AtomicReference(None)
  private val availableSbtVersionsForScala3: AtomicReference[Option[Versions]] = new AtomicReference(None)

  private val isSbtVersionManuallySelected: AtomicBoolean = new AtomicBoolean(false)

  //
  // Raw UI elements
  //

  private val isSbtLoading = new AtomicBoolean(false)
  protected lazy val sbtVersionComboBox: SComboBox[String] = createSComboBoxWithSearchingListRenderer(ListSet(defaultAvailableSbtVersions.versions: _*), None, isSbtLoading)

  private def downloadSbtVersions(disposable: Disposable): Unit = {
    val sbtDownloadVersions: ProgressIndicator => Versions = indicator => {
      Versions.SBT.loadVersionsWithProgress(indicator)
    }
    downloadVersionsAsynchronously(isSbtLoading, disposable, sbtDownloadVersions, Versions.SBT.toString) { v =>
      availableSbtVersions.set(v.toOption)
      availableSbtVersionsForScala3.set(Versions.SBT.sbtVersionsForScala3(v).toOption)
      updateSelectionsAndElementsModelForSbt(v)
    }
  }

  protected val sbtLabelText: String = SbtBundle.message("sbt.settings.sbt")

  protected val downloadSbtSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.sbt.sources"))
  )

  /**
   * Initializes selections and UI elements only once
   */
  override protected def initSelectionsAndUi(contextDisposable: Disposable): Unit = {
    super.initSelectionsAndUi(contextDisposable)
    _initSelectionsAndUi
    downloadSbtVersions(contextDisposable)
  }

  private lazy val _initSelectionsAndUi: Unit = {
    selections.updateSbtVersion(defaultAvailableSbtVersions)

    initUiElementsModel()
    initUiElementsListeners()
  }

  private def updateSelectionsAndElementsModelForSbt(sbtVersions: Versions): Unit = {
    if (!isSbtVersionManuallySelected.get()) {
      selections.sbtVersion = None
      selections.updateSbtVersion(sbtVersions)
    }
    sbtVersionComboBox.updateComboBoxModel(sbtVersions.versions.toArray, selections.sbtVersion)
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
      isSbtVersionManuallySelected.set(true)
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
    sbtVersionComboBox.setItems(supportedSbtVersions.versions.toArray)

    // if we select Scala3 version but had Scala2 version selected before and some sbt version incompatible with Scala3,
    // the latest item from the list will be automatically selected
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    selections.updateSbtVersion(sbtVersions)
  }
}
