package org.jetbrains.plugins.scala
package components

import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.{PluginManager, PluginManagerConfigurable, PluginManagerCore}
import com.intellij.notification._
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.application.{Application, ApplicationInfo, ApplicationManager}
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.pluginBranch._

/**
 * @author Alefas
 * @since 31.10.12
 */
abstract class ScalaPluginVersionVerifier {
  def getSinceVersion: String

  def getUntilVersion: String
}

object ScalaPluginVersionVerifier {
  val EP_NAME: ExtensionPointName[ScalaPluginVersionVerifier] = ExtensionPointName.create("org.intellij.scala.scalaPluginVersionVerifier")

  def getPluginVersion: String = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        PluginManager.getPlugin(pluginLoader.getPluginId).getVersion
      case _ => "Unknown"
    }
  }
}

object ScalaPluginVersionVerifierApplicationComponent {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifierApplicationComponent")
}

object PluginHostsUpdater {

  val baseUrl = "http://www.jetbrains.com/idea/plugins"
  val eapRepo = s"$baseUrl/scala-eap-cassiopeia.xml"
  val nightlyRepo = s"$baseUrl/scala-nightly-cassiopeia.xml"

  def doUpdatePluginHosts(branch: ScalaApplicationSettings.pluginBranch) = {
    val updateSettings = UpdateSettings.getInstance()
    updateSettings.myPluginHosts.remove(eapRepo)
    updateSettings.myPluginHosts.remove(nightlyRepo)

    branch match {
      case Release => // leave default plugin repository
      case EAP     => updateSettings.myPluginHosts.add(eapRepo)
      case Nightly => updateSettings.myPluginHosts.add(nightlyRepo)
    }
  }

  def pluginIsEap = {
    val updateSettings = UpdateSettings.getInstance()
    updateSettings.myPluginHosts.contains(eapRepo)
  }

  def pluginIsNightly = {
    val updateSettings = UpdateSettings.getInstance()
    updateSettings.myPluginHosts.contains(nightlyRepo)
  }

  def pluginIsRelease = !pluginIsEap && !pluginIsNightly

}

class ScalaPluginVersionVerifierApplicationComponent extends ApplicationComponent {
  def getComponentName: String = "ScalaPluginVersionVerifierApplicationComponent"

  def initComponent() {
    case class Version(major: Int, minor: Int, build: Int) {
      def <(v: Version): Boolean = {
        if (major < v.major) true
        else if (major > v.major) false
        else if (minor < v.minor) true
        else if (minor > v.minor) false
        else if (build < v.build) true
        else false
      }
    }

    def askUpdatePluginBranch(): Unit = {
      val infoImpl = ApplicationInfo.getInstance().asInstanceOf[ApplicationInfoImpl]
      val applicationSettings = ScalaApplicationSettings.getInstance()
      if ((infoImpl.isEAP || infoImpl.isBetaOrRC)
          && applicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS
          && PluginHostsUpdater.pluginIsRelease) {
        val message = "Your Idea build seems to be an EAP.\n" +
        "Do you want to use up-to-date Scala Plugin channel?" +
          s"""<p/><a href="EAP">Use EAP</a>\n""" +
//          s"""<p/><a href="Nightly">Use Nightly</a>\n""" +
          s"""<p/><a href="Release">Stay on Release</a>""" +
          s"""<p/><a href="NotNow">Not now(ask me later)</a>"""
        val notification = new Notification("Scala Plugin Update", "Scala Plugin Update", message, NotificationType.INFORMATION, new NotificationListener {
          def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
            notification.expire()
            applicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS = false
            event.getDescription match {
              case "EAP"     => applicationSettings.setScalaPluginBranch(EAP)
              case "Nightly" => applicationSettings.setScalaPluginBranch(Nightly)
              case "Release" => applicationSettings.setScalaPluginBranch(Release)
              case _         => applicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS = true
            }
          }
        })

        Notifications.Bus.notify(notification)
      }
    }

    def parseVersion(version: String): Option[Version] = {
      val VersionRegex = "([0-9]*)[.]([0-9]*)[.]([0-9]*)".r
      version match {
        case VersionRegex(major: String, minor: String, build: String) => Some(Version(major.toInt, minor.toInt, build.toInt))
        case _ => None
      }
    }

    def checkVersion() {
      val versionString = ScalaPluginVersionVerifier.getPluginVersion
      parseVersion(versionString) match {
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
                      s"icompatible with Scala plugin of version $versionString. Do you want to disable ${plugin.getName} plugin?\n" +
                      s"""<p/><a href="Yes">Yes, disable it</a>\n""" +
                      s"""<p/><a href="No">No, leave it enabled</a>"""
                  if (ApplicationManager.getApplication.isUnitTestMode) {
                    ScalaPluginVersionVerifierApplicationComponent.LOG.error(message)
                  } else {
                    val Scala_Group = "Scala Plugin Incompatibility"
                    val app: Application = ApplicationManager.getApplication
                    if (!app.isDisposed) {
                      app.getMessageBus.syncPublisher(Notifications.TOPIC).register(Scala_Group, NotificationDisplayType.STICKY_BALLOON)
                    }
                    Notifications.Bus.register(Scala_Group, NotificationDisplayType.STICKY_BALLOON)
                    val notification = new Notification(Scala_Group, "Incompatible plugin detected", message, NotificationType.ERROR, new NotificationListener {
                      def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
                        notification.expire()
                        val description = event.getDescription
                        description match {
                          case "Yes" =>
                            PluginManagerCore.disablePlugin(plugin.getPluginId.getIdString)
                            PluginManagerConfigurable.showRestartIDEADialog()
                          case "No"  => //do nothing it seems all is ok for the user
                          case _     => //do nothing it seems all is ok for the user
                        }
                      }
                    })

                    Notifications.Bus.notify(notification)
                  }
              }
            }
            parseVersion(extension.getSinceVersion) match {
              case Some(sinceVersion) =>
                if (sinceVersion != version && version < sinceVersion) {
                  wrongVersion()
                }
              case _                  =>
            }

            parseVersion(extension.getUntilVersion) match {
              case Some(untilVersion) =>
                if (untilVersion != version && untilVersion < version) {
                  wrongVersion()
                }
              case _                  =>
            }
          }
        case None          =>
      }
      askUpdatePluginBranch()
    }
    SwingUtilities.invokeLater(new Runnable {
      def run() {
        checkVersion()
      }
    })
  }

  def disposeComponent() {}
}
