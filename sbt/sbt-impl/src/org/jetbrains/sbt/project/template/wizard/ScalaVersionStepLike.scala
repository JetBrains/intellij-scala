package org.jetbrains.sbt.project.template.wizard

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.{GraphProperty, PropertyGraph}
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.{Panel, Row, RowLayout}
import org.jetbrains.sbt.project.template.wizard.kotlin_interop.KotlinInteropUtils
import org.jetbrains.plugins.scala.extensions.applyTo
import org.jetbrains.plugins.scala.{ScalaVersion, isUnitTestMode}
import org.jetbrains.plugins.scala.project.template.{IndentationSyntaxStepLike, ScalaVersionDownloadingDialog}
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.plugins.scala.util.AsynchronousVersionsDownloading
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.{SComboBox, ScalaModuleBuilderSelections}

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.immutable.ListSet

trait ScalaVersionStepLike extends IndentationSyntaxStepLike with AsynchronousVersionsDownloading { self: NewProjectWizardStep =>

  protected def selections: ScalaModuleBuilderSelections =
    ScalaModuleBuilderSelections.default

  protected def defaultAvailableScalaVersions: Seq[String]

  protected val isScalaVersionManuallySelected: AtomicBoolean = AtomicBoolean(false)

  protected val isScalaLoading = AtomicBoolean(false)
  protected lazy val scalaVersionComboBox: SComboBox[String] = createSComboBoxWithSearchingListRenderer(ListSet(defaultAvailableScalaVersions*), None, isScalaLoading)

  @inline private def propertyGraph: PropertyGraph = getPropertyGraph

  protected lazy val scalaVersionProperty: GraphProperty[String] = propertyGraph.property(defaultAvailableScalaVersions.head)
  protected def getScalaVersion: Option[ScalaVersion] = {
    val scalaVersionStr = scalaVersionProperty.get()
    ScalaVersion.fromString(scalaVersionStr)
  }

  private def downloadScalaVersions(disposable: Disposable): Unit = {
    val scalaDownloadVersions: ProgressIndicator => Seq[Version] = indicator => {
      Versions.Scala.loadVersionsWithProgress(indicator)
    }
    downloadVersionsAsynchronously(isScalaLoading, disposable, scalaDownloadVersions, Versions.Scala.toString) { versions =>
      val stringRepresentation = versions.map(_.presentation)
      updateSelectionsAndElementsModelForScala(stringRepresentation)
    }
  }

  private val downloadScalaSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.scala.standard.library.sources"))
  )

  protected val downloadScalaSourcesProperty: GraphProperty[java.lang.Boolean] = propertyGraph.property(java.lang.Boolean.TRUE)

  protected def setUpScalaUI(panel: Panel, downloadSourcesCheckbox: Boolean): Unit = {
    scalaVersionComboBox.addItemListener { e =>
      if (e.getStateChange == java.awt.event.ItemEvent.SELECTED) {
        val selectedScalaVersion = scalaVersionComboBox.getSelectedItemTyped
        val show = selectedScalaVersion.exists(isScala3VersionString)
        setShowIndentationSyntaxCheckBox(show)
      }
    }

    panel.row(SbtBundle.message("sbt.settings.scala"), (row: Row) => {
      row.layout(RowLayout.PARENT_GRID)
      val scalaVersionComboBoxCell = row.cell(scalaVersionComboBox)
      KotlinInteropUtils.bindItem(scalaVersionComboBoxCell, scalaVersionProperty)

      if (downloadSourcesCheckbox) {
        val downloadSourcesCell = row.cell(downloadScalaSourcesCheckbox)
        KotlinInteropUtils.bind(downloadSourcesCell, downloadScalaSourcesProperty)
      }

      kotlin.Unit.INSTANCE
    })
    setupIndentationSyntaxUI(panel)
  }


  /**
   * Initializes selections and UI elements only once
   */
  protected def initSelectionsAndUi(contextDisposable: Disposable): Unit = {
    _initSelectionsAndUi
    if (!isUnitTestMode) {
      downloadScalaVersions(contextDisposable)
    }
  }

  private lazy val _initSelectionsAndUi: Unit = {
    selections.updateScalaVersion(defaultAvailableScalaVersions)

    initUiElementsModel()
    initUiElementsListeners()
  }


  private def updateSelectionsAndElementsModelForScala(scalaVersions: Seq[String]): Unit = {
    if (!isScalaVersionManuallySelected.get()) {
      selections.scalaVersion = None
      selections.updateScalaVersion(scalaVersions)
    }
    scalaVersionComboBox.updateComboBoxModel(scalaVersions.toArray, selections.scalaVersion)
    initSelectedScalaVersion(scalaVersions)
  }

  private def initUiElementsModel(): Unit = {
    initUiElementsModelFrom(selections)
    initSelectedScalaVersion(defaultAvailableScalaVersions)
  }

  private def initUiElementsModelFrom(selections: ScalaModuleBuilderSelections): Unit = {
    scalaVersionComboBox.setSelectedItemSafe(selections.scalaVersion.orNull)
    downloadScalaSourcesCheckbox.setSelected(selections.downloadScalaSdkSources)
  }

  /**
   * Init UI --> Selections binding
   */
  private def initUiElementsListeners(): Unit = {
    scalaVersionComboBox.addActionListener { _ =>
      isScalaVersionManuallySelected.set(true)
      selections.scalaVersion = scalaVersionComboBox.getSelectedItemTyped
    }

    downloadScalaSourcesCheckbox.addChangeListener(_ =>
      selections.downloadScalaSdkSources = downloadScalaSourcesCheckbox.isSelected
    )
  }

  private def initSelectedScalaVersion(scalaVersions: Seq[String]): Unit = {
    selections.scalaVersion match {
      case Some(version) if scalaVersions.contains(version) =>
        scalaVersionComboBox.setSelectedItemSafe(version)

        if (selections.scrollScalaVersionDropdownToTheTop) {
          ScalaVersionDownloadingDialog.UiUtils.scrollToTheTop(scalaVersionComboBox)
        }

        val show = isScala3VersionString(version)
        setShowIndentationSyntaxCheckBox(show)
      case _ if scalaVersionComboBox.getItemCount > 0 =>
        scalaVersionComboBox.setSelectedIndex(0)

        val show = scalaVersionComboBox.getSelectedItemTyped.exists(isScala3VersionString)
        setShowIndentationSyntaxCheckBox(show)
      case _ =>
    }
  }

  private def isScala3VersionString(str: String): Boolean =
    Version(str) >= Version("3.0.0")
}
