package org.jetbrains.plugins.scala.components

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.{org => _, _}
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.PathUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

import java.io.File
import javax.swing.event.HyperlinkEvent
import scala.annotation.nowarn

object ScalaPluginVersionVerifier {

  class Version(private val major: Int, private val minor: Int, private val build: Int) extends Ordered[Version] with Serializable {
    override def compare(that: Version): Int = implicitly[Ordering[(Int, Int, Int)]]
      .compare((major, minor, build), (that.major, that.minor, that.build))

    val presentation: String = if (major == Int.MaxValue) "SNAPSHOT" else s"$major.$minor.$build"

    override def equals(that: Any): Boolean = compare(that.asInstanceOf[Version]) == 0

    override def toString: String = presentation
  }

  object Version {
    object Snapshot extends Version(Int.MaxValue, Int.MaxValue, Int.MaxValue)
    object Zero extends Version(0,0,0)
    def parse(version: String): Option[Version] = {
      val VersionRegex = "(\\d+)[.](\\d+)[.](\\d+)".r
      version match {
        case "VERSION" | "SNAPSHOT" => Some(Snapshot)
        case VersionRegex(major: String, minor: String, build: String) => Some(new Version(major.toInt, minor.toInt, build.toInt))
        case _ => None
      }
    }
  }

  lazy val getPluginVersion: Option[Version] = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        Version.parse(PluginManagerCore.getPlugin(pluginLoader.getPluginId).getVersion)
      case _ => Some(Version.Snapshot)
    }
  }

  def getPluginDescriptor: IdeaPluginDescriptorImpl = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        PluginManagerCore.getPlugin(pluginLoader.getPluginId).asInstanceOf[IdeaPluginDescriptorImpl]
      case other =>
        throw new RuntimeException(s"Wrong plugin classLoader: $other")
    }
  }

  def scalaPluginId: PluginId = getPluginDescriptor.getPluginId

  private[components] val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier")
}

class ScalaPluginVersionVerifierActivity extends RunOnceStartupActivity {
  override def doRunActivity(): Unit = {
    invokeLater {
      ScalaPluginUpdater.upgradeRepo()
      ScalaPluginUpdater.askUpdatePluginBranchIfNeeded()
      checkHaskForcePlugin()
      ScalaPluginUpdater.postCheckIdeaCompatibility()
      ScalaPluginUpdater.setupReporter()
    }
  }

  override protected def doCleanup(): Unit = {}

  private def showIncompatiblePluginNotification(@Nls message: String)(callback: String => Unit): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      ScalaPluginVersionVerifier.LOG.error(message)
    } else {
      val notification = ScalaNotificationGroups.scalaPluginVerifier.createNotification(ScalaBundle.message("incompatible.plugin.detected"), message, NotificationType.ERROR)
      //TODO
      notification.setListener((notification: Notification, event: HyperlinkEvent) => {
        notification.expire()
        val description = event.getDescription
        callback(description)
      }): @nowarn("cat=deprecation")

      Notifications.Bus.notify(notification)
    }
  }

  private def checkHaskForcePlugin(): Unit = {
    val haskforceId = PluginId.findId("com.haskforce")
    val plugin = PluginManagerCore.getPlugin(haskforceId)
    if (plugin == null || !plugin.isEnabled)
      return

    val hasScala211 = try {
      val scalaClass = plugin.getPluginClassLoader.loadClass("scala.Option")
      val pathToJar = PathUtil.getJarPathForClass(scalaClass)
      val file = new File(pathToJar)
      file.getName.contains("-2.11")
    } catch {
      case c: ControlFlowException => throw c
      case _: ClassNotFoundException => false
      case e: Exception =>
        ScalaPluginVersionVerifier.LOG.debug(e)
        false
    }
    if (hasScala211) {
      val DisableHaskForcePlugin = "Disable HaskForce Plugin"
      val DisableScalaPlugin = "Disable Scala Plugin"
      val Ignore = "Ignore"
      val message =
        s"""Plugin ${plugin.getName} of version ${plugin.getVersion} is
           |incompatible with Scala Plugin for IDEA 2017.3
           |${hyperlink(DisableHaskForcePlugin)}
           |${hyperlink(DisableScalaPlugin)}
           |${hyperlink(Ignore)}
           |""".stripMargin
      showIncompatiblePluginNotification(message) {
        case DisableHaskForcePlugin =>
          disablePlugin(haskforceId)
        case DisableScalaPlugin =>
          disablePlugin(ScalaPluginVersionVerifier.scalaPluginId)
        case Ignore =>
        case _ =>
      }
    }

  }

  private def hyperlink(description: String): String =
    s"""<p/><a href="$description">$description</a>"""

  private def disablePlugin(id: PluginId) = {
    PluginManagerCore.disablePlugin(id)
    PluginManagerConfigurable.showRestartDialog()
  }
}
