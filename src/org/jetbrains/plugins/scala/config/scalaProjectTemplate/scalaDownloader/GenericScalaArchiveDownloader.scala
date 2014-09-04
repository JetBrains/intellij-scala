package org.jetbrains.plugins.scala
package config.scalaProjectTemplate.scalaDownloader

import java.io.{File, IOException}
import java.net.URL
import java.util.concurrent.locks.LockSupport
import java.util.zip.ZipFile
import javax.swing.JComponent

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.download.{DownloadableFileService, DownloadableFileSetDescription, DownloadableFileSetVersions}
import com.intellij.util.io.ZipUtil

/**
 * User: Dmitry Naydanov
 * Date: 11/12/12
 */
class GenericScalaArchiveDownloader(localDescriptor: URL, downloadableName: String, project: Project, dialogParent: JComponent) {
  private[this] var versionDescriptions: java.util.List[_ <: DownloadableFileSetDescription] = null
  private[this] val service = DownloadableFileService.getInstance()

  protected val haveToDeleteArchiveAfter = true
  protected val myGroupId: String = null
  
  protected def postProcessDownload(extractedDownloadable: File) { }
  protected def filterFiles(file: DownloadableFileSetDescription): Boolean = true

  protected def showDialog(): Option[DownloadableFileSetDescription] = {
    if (versionDescriptions == null) return None
    
    val myDialog = new MyWebDialog /*DownloadWebLibraryDialog*/(project, versionDescriptions)
    myDialog setTitle s"Download $downloadableName"

    myDialog.show()
    if (!myDialog.isOK) return None

    Some(myDialog.getSelection)
  }

  protected def error(errorMessage: String): String = {
    Notifications.Bus.notify(new Notification("scala", s"Error while downloading $downloadableName",errorMessage, NotificationType.ERROR))
    null
  }

  protected def downloadSelectedVersion(descriptor: Option[DownloadableFileSetDescription]): String = descriptor match {
    case Some(description) =>
      import java.io.File

      val downloader = service.createDownloader(description, project, dialogParent)

      val files = downloader.download()
      if (files == null || files.length == 0) return null

      val path = files(0).getCanonicalPath
      val downloadedArchive = new File(if ((path endsWith "!") || (path endsWith "!/")) path stripSuffix "!" stripSuffix "!/" else path)
      val dirToExtract = downloadedArchive.getParentFile
      val downloadedZipEntries = new ZipFile(downloadedArchive).entries()

      if (!downloadedZipEntries.hasMoreElements) return null

      executeSynchronouslyWithProgress(ZipUtil.extract(downloadedArchive, dirToExtract, null, true), s"Extracting $downloadableName...")
      if (haveToDeleteArchiveAfter) downloadedArchive.delete()
      if (dirToExtract.exists()) {
        val extractedDownloadable = new File(dirToExtract, downloadedZipEntries.nextElement().getName)
        postProcessDownload(extractedDownloadable)
        extractedDownloadable.getCanonicalPath
      } else {
        null
      }
    case None => null
  }

  def download(): String = {
    val setVersions = service.createFileSetVersions(myGroupId, localDescriptor)

    executeSynchronouslyWithProgress({
      val currentThread = Thread.currentThread()

      setVersions.fetchVersions(new DownloadableFileSetVersions.FileSetVersionsCallback[DownloadableFileSetDescription] {
        def onSuccess(versions: java.util.List[_ <: DownloadableFileSetDescription]) {
          import scala.collection.JavaConversions._
          
          versionDescriptions = versions filter filterFiles
          LockSupport unpark currentThread
        }

        override def onError(errorMessage: String) {
          error(errorMessage)
          LockSupport unpark currentThread
        }
      })

      LockSupport.park()
    }, s"Fetching list of $downloadableName versions...")

    if (versionDescriptions != null)
      try downloadSelectedVersion(showDialog()) catch { case ioe: IOException => error(ioe.getMessage) }
    else
      error(s"Cannot download $downloadableName: ")
  }

  private[this] def executeSynchronouslyWithProgress(runnable: =>Unit, title: String) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable {
      def run() { runnable }
    }, title, true, project, dialogParent)
  }
}
