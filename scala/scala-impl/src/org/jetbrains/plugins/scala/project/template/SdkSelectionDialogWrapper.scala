package org.jetbrains.plugins.scala.project.template

import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.sdkdetect.ScalaSdkProvider
import org.jetbrains.plugins.scala.project.sdkdetect.repository.ScalaSdkDetector
import org.jetbrains.plugins.scala.project.template.ScalaVersionDownloadingDialog.ScalaVersionResolveResult
import org.jetbrains.plugins.scala.project.template.sdk_browse.ExplicitSdkSelection

import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing._
import javax.swing.event.ListSelectionEvent

class SdkSelectionDialogWrapper(contextDirectory: VirtualFile) extends DialogWrapper(true) {

  private val myTable = new TableView[SdkChoice]
  private val myTableModel = new SdkTableModel

  private var sdkScanIndicator: Option[ProgressIndicator] = None

  private var mySelectedSdk: Option[ScalaSdkDescriptor] = None


  locally {
    setTitle(ScalaBundle.message("sdk.create.select.files"))
    setModal(true)
    init()

    runSdkScanTask()
  }

  override def createCenterPanel(): JComponent = {
    val panel = new JPanel

    myTable.setModelAndUpdateColumns(myTableModel)
    myTable.getSelectionModel.addListSelectionListener((_: ListSelectionEvent) => updateOkButtonEnabled())
    myTable.getColumnModel.getSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    updateOkButtonEnabled()

    val scrollPane = new JBScrollPane
    scrollPane.setViewportView(myTable)

    panel.setLayout(new GridLayoutManager(1, 1))
    panel.add(scrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(-1, 500), null, 0, false))
    panel
  }

  private def updateOkButtonEnabled(): Unit = {
    val isSdkSelected = myTable.getSelectedRow >= 0
    getOKAction.setEnabled(isSdkSelected)
  }

  override def doCancelAction(): Unit = {
    super.doCancelAction()
    onCancel()
  }

  override def doOKAction(): Unit = {
    super.doOKAction()
    onOK()
  }

  override def createLeftSideActions(): Array[Action] = {
    val downloadAction = new DialogWrapperAction(ScalaBundle.message("scala.sdk.selection.button.download")) {
      override def doAction(actionEvent: ActionEvent): Unit = onDownload()
    }
    val browseAction = new DialogWrapperAction(ScalaBundle.message("scala.sdk.selection.button.browse")) {
      override def doAction(actionEvent: ActionEvent): Unit = onBrowse()
    }
    Array(downloadAction, browseAction)
  }

  private val ScalaLibraryFileNames = Artifact.ScalaLibraryAndModulesArtifacts.map(_.prefix)

  private def onDownload(): Unit = {
    val resolvedScalaVersion = new ScalaVersionDownloadingDialog(this.getContentPanel).showAndGetSelected()
    resolvedScalaVersion.foreach { case ScalaVersionResolveResult(version, compilerJars, librarySourcesJars) =>
      val libraryJars = compilerJars.filter(f => ScalaLibraryFileNames.exists(f.getName.startsWith(_)))
      val sdkDescriptor = ScalaSdkDescriptor(Some(version), compilerJars, libraryJars, librarySourcesJars, Nil /*docs are not downloaded*/)
      closeDialogGracefully(Some(sdkDescriptor))
    }
  }

  private def onBrowse(): Unit = {
    val resultOpt = ExplicitSdkSelection.chooseScalaSdkFiles(myTable)
    resultOpt.foreach { sdk =>
      closeDialogGracefully(Some(sdk))
    }
  }

  private def onOK(): Unit = {
    val sdk =
      if (myTable.getSelectedRowCount > 0)
        Some(myTableModel.getItems.get(myTable.getSelectedRow).sdk)
      else None
    closeDialogGracefully(sdk)
  }

  private def onCancel(): Unit = {
    closeDialogGracefully(None)
  }

  def open(): Option[ScalaSdkDescriptor] = {
    pack()
    show()
    mySelectedSdk
  }

  private def closeDialogGracefully(sdkDescriptor: Option[ScalaSdkDescriptor]): Unit = {
    mySelectedSdk = sdkDescriptor
    cancelCurrentSdkScanning()
    dispose()
  }

  private def runSdkScanTask(): Unit = {
    val scanTask = new SdkScanTask()
    scanTask.queue()
  }

  private def cancelCurrentSdkScanning(): Unit = {
    sdkScanIndicator.foreach(_.cancel())
    sdkScanIndicator = None
  }

  private class SdkScanTask
    extends Task.Backgroundable(null, ScalaBundle.message("sdk.scan.title", ""), true) {

    override def run(indicator: ProgressIndicator): Unit = {
      sdkScanIndicator = Some(indicator)

      val scalaJarDetectors =  ScalaSdkDetector.allDetectors(contextDirectory)
      val scalaSdkProvider = new ScalaSdkProvider(indicator, scalaJarDetectors)
      scalaSdkProvider.discoverSDKs(this.addToTable)

      sdkScanIndicator = None
    }

    private def addToTable(sdkChoice: SdkChoice): Unit = {
      // NOTE: it should be `SwingUtilities.invokeLater`, not `ApplicationManager.getApplication.invokeLater`
      //  otherwise requests will be postponed until dialog is closed (dialogWrapper.show causes it)
      SwingUtilities.invokeLater(() => {
        val previousSelection = myTable.getSelectedRow
        myTableModel.addRow(sdkChoice)
        myTableModel.fireTableDataChanged()
        if (previousSelection >= 0) {
          myTable.getSelectionModel.setSelectionInterval(previousSelection, previousSelection)
        }
      })
    }
  }
}