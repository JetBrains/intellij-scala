package org.jetbrains.plugins.hydra.compiler

import java.awt.event.{ActionEvent, FocusEvent, FocusListener}
import java.net.URL
import javax.swing.event.DocumentEvent

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{Messages, TextComponentAccessor}
import com.intellij.ui.{DocumentAdapter, EditorNotifications}
import org.jetbrains.plugins.hydra.HydraVersions
import org.jetbrains.plugins.hydra.caches.HydraArtifactsCache
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings
import org.jetbrains.plugins.scala.extensions

import scala.util.{Failure, Success, Try}

/**
  * @author Maris Alexandru
  */
class ScalaHydraCompilerConfigurationPanel(project: Project, settings: HydraCompilerSettings, hydraGlobalSettings: HydraApplicationSettings) extends HydraCompilerConfigurationPanel {

  private val fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false)

  private val documentAdapter = new DocumentAdapter {
    override def textChanged(documentEvent: DocumentEvent): Unit =
      downloadButton.setEnabled(getUsername.nonEmpty && getPassword.nonEmpty && getHydraRepository.nonEmpty && getHydraRepositoryRealm.nonEmpty)
  }

  private val focusListener = new FocusListener {
    override def focusGained(e: FocusEvent): Unit = {}

    override def focusLost(e: FocusEvent): Unit = if (getUsername.nonEmpty && getPassword.nonEmpty &&
      (HydraCredentialsManager.getLogin != getUsername ||
        HydraCredentialsManager.getPlainPassword != getPassword ||
        hydraGlobalSettings.getHydraRepositoryUrl != getHydraRepository ||
        hydraGlobalSettings.hydraRepositoryRealm != getHydraRepositoryRealm)) {

      HydraCredentialsManager.setCredentials(getUsername, getPassword)
      hydraGlobalSettings.setHydraRepositopryUrl(getHydraRepository)
      hydraVersionComboBox.setItems(HydraVersions.downloadHydraVersions)
      hydraVersionComboBox.setSelectedItem(settings.hydraVersion)
    }
  }

  hydraGlobalSettings.getState
  setDefaultHydraVersion

  hydraRepository.setText(hydraGlobalSettings.getHydraRepositoryUrl)
  hydraRepository.addFocusListener(focusListener)
  hydraRepository.getDocument.addDocumentListener(documentAdapter)

  realmTextField.setText(hydraGlobalSettings.hydraRepositoryRealm)
  realmTextField.addFocusListener(focusListener)
  realmTextField.getDocument.addDocumentListener(documentAdapter)

  userTextField.addFocusListener(focusListener)
  userTextField.getDocument.addDocumentListener(documentAdapter)

  passwordTextField.getDocument.addDocumentListener(documentAdapter)
  passwordTextField.addFocusListener(focusListener)

  downloadButton.addActionListener((_: ActionEvent) => onDownload())
  noOfCoresComboBox.setItems(Array.range(1, Runtime.getRuntime.availableProcessors() + 1).map(_.toString).sortWith(_ > _))
  sourcePartitionerComboBox.setItems(SourcePartitioner.values.map(_.value).toArray)

  hydraStoreDirectoryField.addBrowseFolderListener("", "Hydra Store Path", null, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)
  hydraStoreDirectoryField.setText(settings.hydraStorePath)

  hydraStoreDirectoryField.getTextField.getDocument.addDocumentListener(new DocumentAdapter() {
    override protected def textChanged(e: DocumentEvent): Unit = {
      hydraStoreDirectoryField.getTextField.setForeground(if (hydraStoreDirectoryField.getText == settings.getDefaultHydraStorePath) getDefaultValueColor
      else getChangedValueColor)
    }
  })


  def selectedVersion: String = hydraVersionComboBox.getSelectedItem.toString

  def setSelectedVersion(version: String): Unit = hydraVersionComboBox.setSelectedItem(version)

  def selectedNoOfCores: String = noOfCoresComboBox.getSelectedItem.toString

  def setSelectedNoOfCores(numberOfCores: String): Unit = noOfCoresComboBox.setSelectedItem(numberOfCores)

  def selectedSourcePartitioner: String = sourcePartitionerComboBox.getSelectedItem.toString

  def setSelectedSourcePartitioner(sourcePartitioner: String): Unit = sourcePartitionerComboBox.setSelectedItem(sourcePartitioner)

  def getHydraStoreDirectory: String = hydraStoreDirectoryField.getText

  def setHydraStoreDirectory(path: String): Unit = hydraStoreDirectoryField.setText(path)

  def getHydraRepository: String = hydraRepository.getText

  def setHydraRepository(repositoryUrl: String): Unit = hydraRepository.setText(repositoryUrl)

  def getHydraRepositoryRealm: String = realmTextField.getText

  def setHydraRepositoryRealm(realm: String): Unit = realmTextField.setText(realm)

  def onDownload(): Unit = {
    Try(new URL(hydraGlobalSettings.getHydraRepositoryUrl)) match {
      case Success(_) => downloadHydraForProjectScalaVersions()
      case _ => Messages.showErrorDialog(contentPanel, s"$getHydraRepository is not a valid URL.", "Invalid URL")
    }
  }

  private def downloadHydraForProjectScalaVersions(): Unit = {
    val scalaVersions = HydraVersions.getSupportedScalaVersions(project)

    if (scalaVersions.isEmpty)
      Messages.showErrorDialog("Could not determine Scala version in this project.", "Hydra Plugin Error")
    else {
      downloadVersionWithProgress(scalaVersions, selectedVersion)
      settings.hydraVersion = selectedVersion
      EditorNotifications.updateAll()
    }
  }

  private def downloadVersionWithProgress(scalaVersions: Seq[String], hydraVersion: String): Unit = {
    val filteredScalaVersionsString = scalaVersions.mkString(", ")
    val scalaVersionsToBeDownloaded = scalaVersions.filterNot(hydraGlobalSettings.artifactPaths.contains(_, hydraVersion))
    val scalaVersionsToBeDownloadedString = scalaVersionsToBeDownloaded.mkString(", ")
    if (scalaVersionsToBeDownloaded.nonEmpty) {
      val result = extensions.withProgressSynchronouslyTry(s"Downloading Hydra $hydraVersion for $scalaVersionsToBeDownloadedString")(downloadVersion(scalaVersionsToBeDownloaded, hydraVersion))
      result match {
        case Failure(exception) => {
          Messages.showErrorDialog(contentPanel, exception.getMessage, s"Error Downloading Hydra $hydraVersion for $scalaVersionsToBeDownloadedString")
        }
        case Success(_) => Messages.showInfoMessage(s"Successfully downloaded Hydra $hydraVersion for $scalaVersionsToBeDownloadedString", "Download Hydra Successful")
      }
    } else {
      Messages.showInfoMessage(s"Hydra $hydraVersion for $filteredScalaVersionsString is already downloaded", "Hydra version already downloaded")
    }
  }

  private def downloadVersion(scalaVersions: Seq[String], hydraVersion: String): (String => Unit) => Unit =
    (listener: (String) => Unit) => scalaVersions.foreach(version => HydraArtifactsCache.downloadIfNotPresent(version, hydraVersion, listener))

  private def setDefaultHydraVersion: Unit = {
    val hydraVersions = HydraVersions.downloadHydraVersions
    hydraVersionComboBox.setItems(hydraVersions)
    setSelectedVersion(hydraVersions.head)
  }
}
