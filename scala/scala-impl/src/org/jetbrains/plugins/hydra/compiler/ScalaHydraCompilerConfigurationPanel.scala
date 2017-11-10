package org.jetbrains.plugins.hydra.compiler

import java.awt.event.ActionEvent
import java.net.URL
import javax.swing.event.DocumentEvent

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{Messages, TextComponentAccessor}
import com.intellij.ui.{DocumentAdapter, EditorNotifications}
import org.jetbrains.plugins.hydra.{HydraDownloader, HydraVersions}
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

  hydraGlobalSettings.getState
  setHydraVersions

  hydraRepository.setText(hydraGlobalSettings.getHydraRepositoryUrl)
  hydraRepository.getDocument.addDocumentListener(documentAdapter)

  realmTextField.setText(hydraGlobalSettings.hydraRepositoryRealm)
  realmTextField.getDocument.addDocumentListener(documentAdapter)

  userTextField.getDocument.addDocumentListener(documentAdapter)

  passwordTextField.getDocument.addDocumentListener(documentAdapter)

  downloadButton.addActionListener((_: ActionEvent) => onDownload())
  downloadVersionButton.addActionListener((_: ActionEvent) => onDownloadVersions())
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

  def getHydraRepositoryName: String = Try(new URL(getHydraRepository)) match {
    case Success(url) => url.getHost
    case _ => ""
  }

  def onDownload(): Unit = {
    Try(new URL(hydraGlobalSettings.getHydraRepositoryUrl)) match {
      case Success(_) => downloadHydraForProjectScalaVersions()
      case _ => Messages.showErrorDialog(contentPanel, s"$getHydraRepository is not a valid URL.", "Invalid URL")
    }
  }

  def onDownloadVersions(): Unit = {
    val hydraVersions = HydraVersions.downloadHydraVersions(getHydraRepository, getUsername, getPassword)
    hydraGlobalSettings.hydraVersions = hydraVersions
    setHydraVersions(hydraVersions)
  }

  private def downloadHydraForProjectScalaVersions(): Unit = {
    val scalaVersions = HydraVersions.getSupportedScalaVersions(project)

    if (scalaVersions.isEmpty)
      Messages.showErrorDialog("Could not determine Scala version in this project.", "Hydra Plugin Error")
    else {
      downloadArtifactsWithProgress(scalaVersions, selectedVersion)
      settings.hydraVersion = selectedVersion
      EditorNotifications.updateAll()
    }
  }

  private def downloadArtifactsWithProgress(scalaVersions: Seq[String], hydraVersion: String): Unit = {
    val filteredScalaVersionsString = scalaVersions.mkString(", ")
    val scalaVersionsToBeDownloaded = scalaVersions.filterNot(hydraGlobalSettings.artifactPaths.contains(_, hydraVersion))
    val scalaVersionsToBeDownloadedString = scalaVersionsToBeDownloaded.mkString(", ")
    if (scalaVersionsToBeDownloaded.nonEmpty) {
      val result = extensions.withProgressSynchronouslyTry(s"Downloading Hydra $hydraVersion for $scalaVersionsToBeDownloadedString")(downloadArtifacts(scalaVersionsToBeDownloaded, hydraVersion))
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

  private def downloadArtifacts(scalaVersions: Seq[String], hydraVersion: String): (String => Unit) => Unit =
    (listener: (String) => Unit) => scalaVersions.foreach(version =>
      HydraDownloader.downloadIfNotPresent(HydraRepositorySettings(getHydraRepositoryName, getHydraRepository,
        getHydraRepositoryRealm, getUsername, getPassword), version, hydraVersion, listener))

  private def setHydraVersions: Unit = {
    val hydraVersions = (hydraGlobalSettings.hydraVersions :+ settings.hydraVersion).distinct
    setHydraVersions(hydraVersions)
  }

  private def setHydraVersions(hydraVersions: Array[String]): Unit = {
    hydraVersionComboBox.setItems(hydraVersions)
    if(settings.hydraVersion.nonEmpty)
      setSelectedVersion(settings.hydraVersion)
    else
      setSelectedVersion(hydraVersions.head)
  }
}

sealed case class HydraRepositorySettings(repositoryName: String, repositoryURL: String, repositoryRealm: String, login: String, password: String)