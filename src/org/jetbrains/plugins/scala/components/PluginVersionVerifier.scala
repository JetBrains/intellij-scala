package org.jetbrains.plugins.scala
package components

import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

import com.intellij.ide.plugins._
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.notification._
import com.intellij.openapi.application.{Application, ApplicationManager}
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * @author Alefas
 * @since 31.10.12
 */
abstract class ScalaPluginVersionVerifier {
  def getSinceVersion: String

  def getUntilVersion: String
}

object ScalaPluginVersionVerifier {

  case class Version(major: Int, minor: Int, build: Int) {
    def <(v: Version): Boolean = {
      if (major < v.major) true
      else if (major > v.major) false
      else if (minor < v.minor) true
      else if (minor > v.minor) false
      else if (build < v.build) true
      else false
    }

    def ==(v: Version): Boolean = {
      build == v.build &&
      minor == v.minor &&
      major == v.major
    }

    def >(other: Version) = !(this < other) && !(this == other)

    override def toString = s"$major.$minor.$build"

  }

  object Version {
    def parse(version: String): Option[Version] = {
      val VersionRegex = "([0-9]*)[.]([0-9]*)[.]([0-9]*)".r
      version match {
        case VersionRegex(major: String, minor: String, build: String) => Some(Version(major.toInt, minor.toInt, build.toInt))
        case _ => None
      }
    }
  }

  val EP_NAME: ExtensionPointName[ScalaPluginVersionVerifier] = ExtensionPointName.create("org.intellij.scala.scalaPluginVersionVerifier")

  def getPluginVersion: Option[Version] = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        Version.parse(PluginManager.getPlugin(pluginLoader.getPluginId).getVersion)
      case _ => None
    }
  }

  def getPluginDescriptor = {
    getClass.getClassLoader match {
      case pluginLoader: PluginClassLoader =>
        PluginManager.getPlugin(pluginLoader.getPluginId).asInstanceOf[IdeaPluginDescriptorImpl]
      case other => throw new RuntimeException(s"Wrong plugin classLoader: $other")
    }

  }
}

object ScalaPluginVersionVerifierApplicationComponent {
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifierApplicationComponent")
}

class ScalaPluginVersionVerifierApplicationComponent extends ApplicationComponent {
  import ScalaPluginVersionVerifier._

  def getComponentName: String = "ScalaPluginVersionVerifierApplicationComponent"

  def initComponent() {

    def checkVersion() {
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
                      s"icompatible with Scala plugin of version $version. Do you want to disable ${plugin.getName} plugin?\n" +
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
                    NotificationGroup.balloonGroup(Scala_Group)
                    val notification = new Notification(Scala_Group, "Incompatible plugin detected", message, NotificationType.ERROR, new NotificationListener {
                      def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
                        notification.expire()
                        val description = event.getDescription
                        description match {
                          case "Yes" =>
                            PluginManagerCore.disablePlugin(plugin.getPluginId.getIdString)
                            PluginManagerConfigurable.showRestartDialog()
                          case "No"  => //do nothing it seems all is ok for the user
                          case _     => //do nothing it seems all is ok for the user
                        }
                      }
                    })

                    Notifications.Bus.notify(notification)
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
    SwingUtilities.invokeLater(new Runnable {
      def run() {
        ScalaPluginUpdater.upgradeRepo()
        checkVersion()
        ScalaPluginUpdater.postCheckIdeaCompatibility()
        ScalaPluginUpdater.setupReporter()
      }
    })
  }

  def disposeComponent() {}
}
