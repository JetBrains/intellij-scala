package org.jetbrains.plugins.scala
package components

import java.io.File

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.plugins._
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification._
import com.intellij.openapi.application.{Application, ApplicationManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.{ExtensionPointName, PluginId}
import com.intellij.util.PathUtil
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.extensions.invokeLater

/**
  * @author Alefas
  * @since 31.10.12
  */
abstract class ScalaPluginVersionVerifier {
  def getSinceVersion: String

  def getUntilVersion: String
}

object ScalaPluginVersionVerifier {

  class Version(private val major: Int, private val minor: Int, private val build: Int) extends Ordered[Version] with Serializable {
    def compare(that: Version): Int = implicitly[Ordering[(Int, Int, Int)]]
      .compare((major, minor, build), (that.major, that.minor, that.build))

    val presentation: String = if (major == Int.MaxValue) "SNAPSHOT" else s"$major.$minor.$build"

    def isSnapshot: Boolean = presentation == "SNAPSHOT"

    override def equals(that: Any): Boolean = compare(that.asInstanceOf[Version]) == 0

    override def toString: String = presentation
  }

  object Version {
    object Snapshot extends Version(Int.MaxValue, Int.MaxValue, Int.MaxValue)
    def parse(version: String): Option[Version] = {
      val VersionRegex = "(\\d+)[.](\\d+)[.](\\d+)".r
      version match {
        case "VERSION" | "SNAPSHOT" => Some(Snapshot)
        case VersionRegex(major: String, minor: String, build: String) => Some(new Version(major.toInt, minor.toInt, build.toInt))
        case _ => None
      }
    }
  }

  val EP_NAME: ExtensionPointName[ScalaPluginVersionVerifier] = ExtensionPointName.create("org.intellij.scala.scalaPluginVersionVerifier")

  lazy val getPluginVersion: Option[Version] = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        Version.parse(PluginManager.getPlugin(pluginLoader.getPluginId).getVersion)
      case _ => Some(Version.Snapshot)
    }
  }

  def getPluginDescriptor: IdeaPluginDescriptorImpl = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        PluginManager.getPlugin(pluginLoader.getPluginId).asInstanceOf[IdeaPluginDescriptorImpl]
      case other => throw new RuntimeException(s"Wrong plugin classLoader: $other")
    }
  }

  def scalaPluginId: String = getPluginDescriptor.getPluginId.getIdString

  private[components] val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier")
}

class ScalaPluginVersionVerifierListener extends ApplicationInitializedListener {
  override def componentsInitialized(): Unit = {
    invokeLater {
      ScalaPluginUpdater.upgradeRepo()
      checkVersion()
      checkHaskForcePlugin()
      ScalaPluginUpdater.postCheckIdeaCompatibility()
      ScalaPluginUpdater.setupReporter()
    }
  }

  private def checkVersion() {
    import ScalaPluginVersionVerifier._

    ScalaPluginVersionVerifier.getPluginVersion match {
      case Some(version) =>
        val extensions = ScalaPluginVersionVerifier.EP_NAME.getExtensions

        for (extension <- extensions) {
          var failed = false
          def wrongVersion() {
            failed = true
            extension.getClass.getClassLoader match {
              case pluginLoader: PluginClassLoader =>
                val plugin = PluginManager.getPlugin(pluginLoader.getPluginId)
                val message =
                  s"Plugin ${plugin.getName} of version ${plugin.getVersion} is " +
                    s"incompatible with Scala plugin of version $version. Do you want to disable ${plugin.getName} plugin?\n" +
                    s"""<p/><a href="Yes">Yes, disable it</a>\n""" +
                    s"""<p/><a href="No">No, leave it enabled</a>"""

                showIncompatiblePluginNotification(plugin, message) {
                  case "Yes" =>
                    disablePlugin(plugin.getPluginId.getIdString)
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
    ScalaPluginUpdater.askUpdatePluginBranch()
  }

  private def showIncompatiblePluginNotification(plugin: IdeaPluginDescriptor, message: String)(callback: String => Unit): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      ScalaPluginVersionVerifier.LOG.error(message)
    } else {
      val Scala_Group = "Scala Plugin Incompatibility"
      val app: Application = ApplicationManager.getApplication
      if (!app.isDisposed) {
        app.getMessageBus.syncPublisher(Notifications.TOPIC).register(Scala_Group, NotificationDisplayType.STICKY_BALLOON)
      }
      NotificationGroup.balloonGroup(Scala_Group)
      val notification = new Notification(Scala_Group, "Incompatible plugin detected", message, NotificationType.ERROR, new NotificationListener {
        def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
          notification.expire()
          val description = event.getDescription
          callback(description)
        }
      })

      Notifications.Bus.notify(notification)
    }
  }

  private def checkHaskForcePlugin(): Unit = {
    val haskforceId = "com.haskforce"
    val plugin = PluginManager.getPlugin(PluginId.findId(haskforceId))
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

  private def disablePlugin(id: String) = {
    PluginManagerCore.disablePlugin(id)
    PluginManagerConfigurable.showRestartDialog()
  }
}
