package org.jetbrains.sbt.project.template.wizard

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.{EmptyProgressIndicator, ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.{JBCheckBox, JBLabel}
import com.intellij.ui.{AnimatedIcon, DocumentAdapter}
import com.intellij.util.ui.ReloadableComboBoxPanel
import com.intellij.util.ui.ReloadablePanel.DataProvider
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.{PackagePrefixStepLike, ScalaVersionDownloadingDialog}
import org.jetbrains.plugins.scala.util.AsynchronousDownloadingUtil
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.{SComboBox, SbtModuleBuilderSelections}

import java.awt.{CardLayout, Component, FlowLayout}
import java.{lang, util}
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.{DefaultComboBoxModel, JComboBox, JPanel}
import scala.concurrent.Promise
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SetHasAsJava}
import scala.util.{Success, Try}

private[template] trait SbtModuleStepLike extends PackagePrefixStepLike {

  protected def selections: SbtModuleBuilderSelections

  //
  // Scala & Sbt versions, initialized lazily from the Internet
  // TODO: improve this in SCL-19189
  //
  protected val defaultAvailableScalaVersions: Versions
  protected var availableScalaVersions: Option[Versions] = None

  protected var availableSbtVersions: Option[Versions] = None
  protected var availableSbtVersionsForScala3: Option[Versions] = None
  protected val defaultAvailableSbtVersions: Versions
  protected val defaultAvailableSbtVersionsForScala3: Versions

  private def getSbtVersions: Versions = availableSbtVersions.getOrElse(defaultAvailableSbtVersions)
  private def getScalaVersions: Versions = availableScalaVersions.getOrElse(defaultAvailableScalaVersions)
  private val isSbtComboBoxModified: AtomicBoolean = new AtomicBoolean(false)
  private val isScalaComboBoxModified: AtomicBoolean = new AtomicBoolean(false)

  //
  // Raw UI elements
  //

  protected lazy val sbtLoadingLabel: JBLabel = AsynchronousDownloadingUtil.createLabelWithLoadingIcon(ScalaBundle.message("title.fetching.available.this.versions", Versions.SBT))
  protected lazy val scalaLoadingLabel: JBLabel = AsynchronousDownloadingUtil.createLabelWithLoadingIcon(ScalaBundle.message("title.fetching.available.this.versions", Versions.Scala))
  protected val sbtVersionComboBox: SComboBox[String] = new SComboBox[String](150)
  protected lazy val scalaVersionComboBox: SComboBox[String] = new SComboBox[String](150)

  private def runDownloadVersionsTasks(disposable: Disposable): Unit = {
    val sbtIndicator = new EmptyProgressIndicator
    val scalaIndicator = new EmptyProgressIndicator
    Disposer.register(disposable, () => {
      sbtIndicator.cancel()
      scalaIndicator.cancel()

    })
    //downloadVersionsAsynchronously(Versions.SBT, sbtLoadingLabel, sbtIndicator, 10.seconds)
    downloadVersionsAsynchronously(Versions.Scala, scalaLoadingLabel, scalaIndicator, 50.seconds)
  }

  def reloadablePanel(dis: Disposable) = {
    val panel = new ReloadableComboBoxPanel[String] {
      override def doUpdateValues(values: util.Set[String]): Unit = {
        values.asScala.foreach(t => myComboBox.addItem(t))
        myComboBox.addItem("veeeerryyy longgg object")
      }
    }
    panel.setDataProvider(new DataProvider[String]() {
      override def getCachedValues: java.util.Set[String] = availableSbtVersions.getOrElse(defaultAvailableSbtVersions).versions.toSet.asJava

      override def updateValuesAsynchronously(): Unit = {
        runSbtDownloading(panel, dis, Versions.SBT, sbtLoadingLabel, 10.seconds)
      }
    })
    panel.reloadValuesInBackground()

    //first I was trying to return it, later I switched to simpler case with myTestingPanel. The result is the same for both cases
   val reloadableComboBoxPanel = panel.getMainPanel

    val myTestingPanel = new DialogPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    val cb = new JComboBox()
    myTestingPanel.setAlignmentX(Component.LEFT_ALIGNMENT)
    myTestingPanel.add(cb)
    reloadableComboBoxPanel
  }

  private def runSbtDownloading(panel: ReloadableComboBoxPanel[String], dis: Disposable, kind: Versions.Kind, iconLabel: JBLabel, timeout: FiniteDuration): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val promise: Promise[Versions] = Promise[Versions]()
//    val manager = ProgressManager.getInstance
//    val task = AsynchronousDownloadingUtil.downloadVersionsTask(ScalaBundle.message("title.fetching.available.this.versions", kind), promise, iconLabel, timeout) {
//      kind.loadVersionsWithoutProgress(new EmptyProgressIndicator, timeout)
//    }

    val runnable = new Runnable {
      override def run(): Unit = {
        promise.tryComplete(Try(kind.loadVersionsWithoutProgress(new EmptyProgressIndicator, timeout)))
      }
    }
    val resultFuture = ApplicationManager.getApplication.executeOnPooledThread(runnable)
    Disposer.register(dis, () => {
      resultFuture.cancel(true)
    })
    promise.future.onComplete {
      case Success(v) =>
        kind match {
          case Versions.SBT =>
            panel.onUpdateValues(v.versions.toSet.asJava)
            availableSbtVersions = v.toOption
            availableSbtVersionsForScala3 = Versions.SBT.sbtVersionsForScala3(availableSbtVersions.get).toOption
            updateSelectionsAndElementsModelForSbt()
          case Versions.Scala =>
            availableScalaVersions = v.copy(versions = v.versions).toOption
            updateSelectionsAndElementsModelForScala()
        }
      case _ =>
        val o = "demoText"
    }
  }
  private def downloadVersionsAsynchronously(kind: Versions.Kind, iconLabel: JBLabel, indicator: ProgressIndicator, timeout: FiniteDuration): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val promise: Promise[Versions] = Promise[Versions]()
    val manager = ProgressManager.getInstance
    val task = AsynchronousDownloadingUtil.downloadVersionsTask(ScalaBundle.message("title.fetching.available.this.versions", kind), promise, iconLabel, timeout) {
      kind.loadVersionsWithoutProgress(indicator, timeout)
    }
    manager.runProcessWithProgressAsynchronously(task, indicator)
    promise.future.onComplete {
        case Success(v) =>
          kind match {
            case Versions.SBT =>
              availableSbtVersions = v.toOption
              availableSbtVersionsForScala3 = Versions.SBT.sbtVersionsForScala3(availableSbtVersions.get).toOption
              updateSelectionsAndElementsModelForSbt()
            case Versions.Scala =>
              availableScalaVersions = v.copy(versions = v.versions).toOption
              updateSelectionsAndElementsModelForScala()
          }
        case _ =>
          val o = "demoText"
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
    runDownloadVersionsTasks(contextDisposable)
  }

  private lazy val _initSelectionsAndUi: Unit = {
    selections.update(Versions.SBT, defaultAvailableSbtVersions)
    selections.update(Versions.Scala, defaultAvailableScalaVersions)

    initUiElementsModel(defaultAvailableSbtVersions, defaultAvailableScalaVersions)
    initUiElementsListeners()
  }

  private def updateSelectionsAndElementsModelForSbt(): Unit = {
    val sbtVersions = getSbtVersions
    if (!isSbtComboBoxModified.get()) {
      selections.sbtVersion = None
      selections.update(Versions.SBT, sbtVersions)
    }

    updateModelInComboBox(sbtVersionComboBox, sbtVersions.versions, selections.sbtVersion)
    updateSupportedSbtVersionsForSelectedScalaVersion(sbtVersions)
  }

  private def updateSelectionsAndElementsModelForScala(): Unit = {
    val scalaVersions = getScalaVersions
    if (!isScalaComboBoxModified.get()) {
      selections.scalaVersion = None
      selections.update(Versions.Scala, scalaVersions)
    }

    updateModelInComboBox(scalaVersionComboBox, scalaVersions.versions, selections.scalaVersion)
    initSelectedScalaVersion(scalaVersions)
  }

  private def updateModelInComboBox(comboBox: SComboBox[String], modelElements: Seq[String], selectedElement: Option[String]): Unit = {
    val model = new DefaultComboBoxModel[String](modelElements.toArray)
    selectedElement.foreach(model.setSelectedItem(_))
    comboBox.setModel(model)
  }

  private def initUiElementsModel(sbtVersions: Versions, scalaVersions: Versions): Unit = {
    sbtVersionComboBox.setItems(sbtVersions.versions.toArray)
    scalaVersionComboBox.setItems(scalaVersions.versions.toArray)

    initUiElementsModelFrom(selections)
    initSelectedScalaVersion(scalaVersions)
    updateSupportedSbtVersionsForSelectedScalaVersion(sbtVersions)
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
      isSbtComboBoxModified.set(true)
      selections.sbtVersion = sbtVersionComboBox.getSelectedItemTyped
    }
    scalaVersionComboBox.addActionListener { _ =>
      isScalaComboBoxModified.set(true)
      selections.scalaVersion = scalaVersionComboBox.getSelectedItemTyped

      updateSupportedSbtVersionsForSelectedScalaVersion(getSbtVersions)

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
  private def updateSupportedSbtVersionsForSelectedScalaVersion(sbtVersions: Versions): Unit = {
    val isScala3Selected = selections.scalaVersion.exists(isScala3Version)
    val supportedSbtVersions = if (isScala3Selected) availableSbtVersionsForScala3.getOrElse(defaultAvailableSbtVersionsForScala3) else sbtVersions
    sbtVersionComboBox.setItems(supportedSbtVersions.versions.toArray)

    // if we select Scala3 version but had Scala2 version selected before and some sbt version incompatible with Scala3,
    // the latest item from the list will be automatically selected
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    selections.update(Versions.SBT, sbtVersions)
  }
}