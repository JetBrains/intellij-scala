package org.jetbrains.plugins.scala
package config

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.diagnostic.Logger

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
  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.config.ScalaPluginVersionVerifierApplicationComponent")
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
    def parseVersion(version: String): Option[Version] = {
      val VersionRegex = "([0-9]*)[.]([0-9]*)[.]([0-9]*)".r
      version match {
        case VersionRegex(major: String, minor: String, build: String) => Some(Version(major.toInt, minor.toInt, build.toInt))
        case _ => None
      }
    }

    parseVersion(ScalaPluginVersionVerifier.getPluginVersion) match {
      case Some(version) =>
        val extensions = ScalaPluginVersionVerifier.EP_NAME.getExtensions

        for (extension <- extensions) {
          var failed = false
          def wrongVersion() {
            failed = true
            extension.getClass.getClassLoader match {
              case pluginLoader: PluginClassLoader =>
                val plugin = PluginManager.getPlugin(pluginLoader.getPluginId)
                val message = s"Plugin ${plugin.getName} is incompatible with Scala plugin of version $version"
                if (ApplicationManager.getApplication.isUnitTestMode) {
                  ScalaPluginVersionVerifierApplicationComponent.LOG.error(message)
                } else {
                  PluginManager.disablePlugin(plugin.getPluginId.getIdString)
                  Messages.showErrorDialog(message, "Incompatible plugin")
                }
            }
          }
          parseVersion(extension.getSinceVersion) match {
            case Some(sinceVersion) =>
              if (sinceVersion != version && version < sinceVersion) {
                wrongVersion()
              }
            case _ =>
          }

          parseVersion(extension.getUntilVersion) match {
            case Some(untilVersion) =>
              if (untilVersion != version && untilVersion < version) {
                wrongVersion()
              }
            case _ =>
          }
        }
      case None =>
    }

  }

  def disposeComponent() {}
}
