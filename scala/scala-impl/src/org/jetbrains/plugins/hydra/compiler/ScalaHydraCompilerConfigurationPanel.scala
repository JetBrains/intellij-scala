package org.jetbrains.plugins.hydra.compiler

import java.awt.event.ActionEvent
import java.net.URL

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.{DocumentAdapter, EditorNotifications}
import com.intellij.util.net.HttpConfigurable
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import org.jetbrains.plugins.hydra.settings.HydraApplicationSettings
import org.jetbrains.plugins.hydra.{HydraDownloader, HydraVersions}
import org.jetbrains.plugins.scala.extensions.{StringsExt, withProgressSynchronouslyTry}

import scala.util.{Failure, Success, Try}

/**
  * @author Maris Alexandru
  */
class ScalaHydraCompilerConfigurationPanel(project: Project, settings: HydraCompilerSettings, hydraGlobalSettings: HydraApplicationSettings) extends HydraCompilerConfigurationPanel {

  private val documentAdapter = new DocumentAdapter {
    override def textChanged(documentEvent: DocumentEvent): Unit =
      downloadButton.setEnabled(getUsername.nonEmpty && getPassword.nonEmpty && getHydraRepository.nonEmpty && getHydraRepositoryRealm.nonEmpty)
  }

  hydraGlobalSettings.getState

  HydraCompilerSettingsManager.setHydraLogSystemProperty(project)

  hydraRepository.setText(hydraGlobalSettings.getHydraRepositoryUrl)
  hydraRepository.getDocument.addDocumentListener(documentAdapter)

  realmTextField.setText(hydraGlobalSettings.hydraRepositoryRealm)
  realmTextField.getDocument.addDocumentListener(documentAdapter)

  userTextField.getDocument.addDocumentListener(documentAdapter)

  passwordTextField.getDocument.addDocumentListener(documentAdapter)

  versionTextField.setText(settings.hydraVersion)

  downloadButton.addActionListener((_: ActionEvent) => onDownload())
  checkConnectionButton.addActionListener((_: ActionEvent) => onCheck())

  noOfCoresComboBox.setItems(Array.range(1, Runtime.getRuntime.availableProcessors() + 1).map(_.toString).sortWith(_ > _))
  sourcePartitionerComboBox.setItems(SourcePartitioner.values.map(_.value).toArray)

  def selectedNoOfCores: String = noOfCoresComboBox.getSelectedItem.toString

  def setSelectedNoOfCores(numberOfCores: String): Unit = noOfCoresComboBox.setSelectedItem(numberOfCores)

  def selectedSourcePartitioner: String = sourcePartitionerComboBox.getSelectedItem.toString

  def setSelectedSourcePartitioner(sourcePartitioner: String): Unit = sourcePartitionerComboBox.setSelectedItem(sourcePartitioner)

  def getHydraRepository: String = hydraRepository.getText

  def setHydraRepository(repositoryUrl: String): Unit = hydraRepository.setText(repositoryUrl)

  def getHydraRepositoryRealm: String = realmTextField.getText

  def setHydraRepositoryRealm(realm: String): Unit = realmTextField.setText(realm)

  def getHydraRepositoryName: String = Try(new URL(getHydraRepository)) match {
    case Success(url) => url.getHost
    case _ => ""
  }

  def getHydraVersion: String = versionTextField.getText

  def setHydraVersion(version: String) = versionTextField.setText(version)

  import Messages._

  def onDownload(): Unit = {
    Try(new URL(hydraGlobalSettings.getHydraRepositoryUrl)) match {
      case Success(_) => downloadHydraForProjectScalaVersions()
      case _ => showErrorDialog(contentPanel, s"$getHydraRepository is not a valid URL.", "Invalid URL")
    }
  }

  def onCheck(): Unit = {
    val settings = HttpConfigurable.getInstance
    val title = "Check Credential and Repository Settings"

    checkConnectionButton.setEnabled(false)

    ApplicationManager.getApplication.executeOnPooledThread(new Runnable {
      override def run(): Unit = {
        Try({
          val connection = settings.openHttpConnection(getHydraRepository)
          val credentials = s"$getUsername:$getPassword"
          connection.setRequestProperty("Authorization", s"Basic ${HydraCredentialsManager.encode(credentials)}")
          connection.getInputStream
          connection.disconnect()
        }) match {
          case Success(_) =>
            SwingUtilities.invokeLater(() => showInfoMessage(contentPanel, "Connection successful", title))
          case Failure(_) =>
            SwingUtilities.invokeLater(() => showErrorDialog(contentPanel, "Connection failed: Check your credentials and repository URL", title))
        }

        checkConnectionButton.setEnabled(true)
        checkConnectionButton.setText("Check connection")
      }
    }
    )
  }

  private def downloadHydraForProjectScalaVersions(): Unit =
    HydraVersions.getSupportedScalaVersions(project) match {
      case Seq() =>
        showErrorDialog("Could not determine Scala version in this project.", "Hydra Plugin Error")
      case versions =>
        val hydraVersion = getHydraVersion

        versions.filterNot(hydraGlobalSettings.artifactPaths.contains(_, hydraVersion)) match {
          case downloadedVersions@Seq() =>
            showInfoMessage(s"Hydra $hydraVersion for ${downloadedVersions.commaSeparated()} is already downloaded", "Hydra version already downloaded")
          case versionsToDownload =>
            downloadArtifactsWithProgress(versionsToDownload, hydraVersion)
        }

        settings.hydraVersion = hydraVersion
        EditorNotifications.updateAll()
    }

  private def downloadArtifactsWithProgress(scalaVersions: Seq[String], hydraVersion: String): Unit = {
    val versionsText = scalaVersions.commaSeparated()
    val result = withProgressSynchronouslyTry(s"Downloading Hydra $hydraVersion for $versionsText") { manager =>
      scalaVersions.foreach(downloadIfNotPresent(_, manager))
    }

    result match {
      case Failure(exception) =>
        showErrorDialog(contentPanel, exception.getMessage, s"Error Downloading Hydra $hydraVersion for $versionsText")
      case Success(_) =>
        showInfoMessage(s"Successfully downloaded Hydra $hydraVersion for $versionsText", "Download Hydra Successful")
    }
  }

  private def downloadIfNotPresent(scalaVersion: String, manager: ProgressManager): Unit =
    HydraDownloader.downloadIfNotPresent(scalaVersion, manager)(
      getHydraVersion,
      getHydraRepositoryName,
      getHydraRepository,
      getHydraRepositoryRealm,
      getUsername,
      getPassword
    )
}
