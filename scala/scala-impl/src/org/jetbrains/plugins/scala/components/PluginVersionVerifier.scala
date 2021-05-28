package org.jetbrains.plugins.scala
package components

import com.intellij.ide.plugins._
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.PathUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

import java.io.File
import javax.swing.event.HyperlinkEvent

/**
  * @author Alefas
  * @since 31.10.12
  */
abstract class ScalaPluginVersionVerifier {
  def getSinceVersion: String

  def getUntilVersion: String
}

object ScalaPluginVersionVerifier
  extends ExtensionPointDeclaration[ScalaPluginVersionVerifier]("org.intellij.scala.scalaPluginVersionVerifier") {

  class Version(private val major: Int, private val minor: Int, private val build: Int) extends Ordered[Version] with Serializable {
    override def compare(that: Version): Int = implicitly[Ordering[(Int, Int, Int)]]
      .compare((major, minor, build), (that.major, that.minor, that.build))

    val presentation: String = if (major == Int.MaxValue) "SNAPSHOT" else s"$major.$minor.$build"

    def isSnapshot: Boolean = presentation == "SNAPSHOT"

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
      case other => throw new RuntimeException(s"Wrong plugin classLoader: $other")
    }
  }

  def scalaPluginId: PluginId = getPluginDescriptor.getPluginId

  private[components] val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier")
}

class ScalaPluginVersionVerifierActivity extends RunOnceStartupActivity {
  override def doRunActivity(): Unit = {
    invokeLater {
      ScalaPluginUpdater.upgradeRepo()
      checkVersion()
      checkHaskForcePlugin()
      ScalaPluginUpdater.postCheckIdeaCompatibility()
      ScalaPluginUpdater.setupReporter()
    }
  }

  override protected def doCleanup(): Unit = {}

  private def checkVersion(): Unit = {
    import ScalaPluginVersionVerifier._

    ScalaPluginVersionVerifier.getPluginVersion match {
      case Some(version) =>
        val extensions = ScalaPluginVersionVerifier.implementations

        for (extension <- extensions) {
          var failed = false
          def wrongVersion(): Unit = {
            failed = true
            extension.getClass.getClassLoader match {
              case pluginLoader: PluginClassLoader =>
                val plugin = PluginManagerCore.getPlugin(pluginLoader.getPluginId)
                val message =
                  s"Plugin ${plugin.getName} of version ${plugin.getVersion} is " +
                    s"incompatible with Scala plugin of version $version. Do you want to disable ${plugin.getName} plugin?\n" +
                    s"""<p/><a href="Yes">Yes, disable it</a>\n""" +
                    s"""<p/><a href="No">No, leave it enabled</a>"""

                showIncompatiblePluginNotification(plugin, message) {
                  case "Yes" =>
                    disablePlugin(plugin.getPluginId)
                  case "No" => //do nothing it seems all is ok for the user
                  case _ => //do nothing it seems all is ok for the user
                }
            }
          }
          Version.parse(extension.getSinceVersion) match {
            case Some(sinceVersion) =>
              if (sinceVersion != version && version < sinceVersion) {
                wrongVersion()
              }
            case _                  =>
          }

          Version.parse(extension.getUntilVersion) match {
            case Some(untilVersion) =>
              if (untilVersion != version && untilVersion < version) {
                wrongVersion()
              }
            case _                  =>
          }
        }
      case None          =>
    }
    ScalaPluginUpdater.askUpdatePluginBranchIfNeeded()
  }

  private def showIncompatiblePluginNotification(plugin: IdeaPluginDescriptor, @Nls message: String)(callback: String => Unit): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      ScalaPluginVersionVerifier.LOG.error(message)
    } else {
      val notification = ScalaNotificationGroups.stickyBalloonGroup.createNotification(ScalaBundle.message("incompatible.plugin.detected"), message, NotificationType.ERROR).setListener((notification: Notification, event: HyperlinkEvent) => {
        notification.expire()
        val description = event.getDescription
        callback(description)
      })

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
      showIncompatiblePluginNotification(plugin, message) {
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
