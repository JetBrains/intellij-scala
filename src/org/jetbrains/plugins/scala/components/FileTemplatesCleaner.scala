package org.jetbrains.plugins.scala.components

import org.jetbrains.plugins.scala.config.FileAPI._
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.notification.{NotificationListener, NotificationType, Notification, Notifications}
import javax.swing.event.HyperlinkEvent
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.DesktopUtils
import java.io.File
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.application.{ApplicationManager, PathManager}

/**
 * Pavel Fatin
 */

//TODO Housekeeping class, should be remvoved in the future
class FileTemplatesCleaner extends ApplicationComponent {
  @Language("HTML")
  private val Message = """
  <html>
   <body>
    <p>To fix <a href="http://devnet.jetbrains.net/message/5286355">new class creation problem</a> Scala plugin needs to:
    <ol>
      <li>update file templates,</li>
      <li>invalidate caches,</li>
      <li>restart IDEA.</li>
    </ol></p>
    <p style="color: #AA0000;">WARNING: Local History will be also cleared</p>
    <br>
    <a href=''>Perform the required actions</a>
   </body>
  </html>"""

  private val TemplatesRoot = file(PathManager.getConfigPath) / "fileTemplates" / "internal"

  def getComponentName = "FileTemplatesCleaner"

  def initComponent() {
    if(!orphanFiles.isEmpty) {
      Notifications.Bus.notify(new Notification("scala", "File Templates update required",
        Message, NotificationType.WARNING, Listener))
    }
  }

  private def rootFiles: Seq[File] = {
    if(TemplatesRoot.exists) {
      TemplatesRoot.listFiles
    } else {
      Seq.empty
    }
  }

  private def orphanFiles: Seq[File] = rootFiles.filter(_.getName.startsWith("Scala"))

  private def clean() {
    val deleteRoot = rootFiles.size == orphanFiles.size
    orphanFiles.foreach(_.deleteForSure())
    if(deleteRoot) TemplatesRoot.deleteForSure()
    FSRecords.invalidateCaches
    ApplicationManager.getApplication.restart
  }

  def disposeComponent() {}

  private object Listener extends NotificationListener {
    def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
      val url = event.getURL
      if (url == null) {
        clean()
        notification.expire()
      } else {
        DesktopUtils.browse(url)
      }
    }
  }
}