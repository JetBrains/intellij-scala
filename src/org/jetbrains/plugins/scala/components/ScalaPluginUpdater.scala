package org.jetbrains.plugins.scala.components

import java.io.{File, IOException}
import java.lang.reflect.Field
import javax.swing.event.HyperlinkEvent

import com.intellij.ide.plugins.{IdeaPluginDescriptorImpl, PluginInstaller, PluginManagerMain, PluginManagerUISettings}
import com.intellij.notification._
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.application.{Application, ApplicationInfo, ApplicationManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.{UpdateChecker, UpdateSettings}
import com.intellij.openapi.util.JDOMExternalizableStringList
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.pluginBranch._

import scala.xml.transform.{RewriteRule, RuleTransformer}

object ScalaPluginUpdater {
  private val LOG = Logger.getInstance(getClass)

  val baseUrl = "http://www.jetbrains.com/idea/plugins"
  val eapRepo = s"$baseUrl/scala-eap-cassiopeia.xml"
  val nightlyRepo = s"$baseUrl/scala-nightly-cassiopeia.xml"

  def doUpdatePluginHosts(branch: ScalaApplicationSettings.pluginBranch) = {
    // update hack - set plugin version to 0 when downgrading
    if (getScalaPluginBranch.compareTo(branch) > 0) ScalaPluginUpdater.patchPluginVersion()

    val updateSettings = UpdateSettings.getInstance()
    updateSettings.myPluginHosts.remove(eapRepo)
    updateSettings.myPluginHosts.remove(nightlyRepo)

    branch match {
      case Release => // leave default plugin repository
      case EAP     => updateSettings.myPluginHosts.add(eapRepo)
      case Nightly => updateSettings.myPluginHosts.add(nightlyRepo)
    }
  }

  def doUpdatePluginHostsAndCheck(branch: ScalaApplicationSettings.pluginBranch) = {
    doUpdatePluginHosts(branch)
    UpdateChecker.updateAndShowResult()
  }

  def getScalaPluginBranch: ScalaApplicationSettings.pluginBranch = {
    if (ScalaPluginUpdater.pluginIsEap) EAP
    else if (ScalaPluginUpdater.pluginIsNightly) Nightly
    else Release
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

  def uninstallPlugin() = {
    val descriptor = ScalaPluginVersionVerifier.getPluginDescriptor
    val pluginId: PluginId = descriptor.getPluginId
    descriptor.setDeleted(true)

    try {
      PluginInstaller.prepareToUninstall(pluginId)
      val installedPlugins: JDOMExternalizableStringList = PluginManagerUISettings.getInstance.getInstalledPlugins
      val pluginIdString: String = pluginId.getIdString
      while (installedPlugins.contains(pluginIdString)) {
        installedPlugins.remove(pluginIdString)
      }
    }
    catch {
      case e1: IOException => PluginManagerMain.LOG.error(e1)
    }
  }

  // this hack uses fake plugin.xml deserialization to downgrade plugin version
  // at least it's not using reflection
  def patchPluginVersion() = {
    import scala.xml._
    val versionPatcher = new RewriteRule {
      override def transform(n: Node): NodeSeq = n match {
        case <version>{_}</version> => <version>{"0.0.0"}</version>
        case <include/> => NodeSeq.Empty // relative path includes break temp file parsing
        case other => other
      }
    }
    val descriptor = ScalaPluginVersionVerifier.getPluginDescriptor
    val stream = getClass.getClassLoader.getResource("META-INF/plugin.xml").openStream()
    val document = new RuleTransformer(versionPatcher).transform(XML.load(stream))
    val tempFile = File.createTempFile("plugin", "xml")
    XML.save(tempFile.getAbsolutePath, document.head)
    descriptor.readExternal(tempFile.toURI.toURL)
    tempFile.delete()
  }

  def patchPluginVersionReflection() = {
    // crime of reflection goes below - workaround until force updating is available
    try {
      val hack: Field = classOf[IdeaPluginDescriptorImpl].getDeclaredField("myVersion")
      hack.setAccessible(true)
      hack.set(ScalaPluginVersionVerifier.getPluginDescriptor, "0.0.0")
    }
    catch {
      case _: NoSuchFieldException | _: IllegalAccessException  =>
        Notifications.Bus.notify(new Notification("Scala", "Scala Plugin Update", "Please remove and reinstall Scala plugin to finish downgrading", NotificationType.INFORMATION))
    }
  }

  def askUpdatePluginBranch(): Unit = {
    val infoImpl = ApplicationInfo.getInstance().asInstanceOf[ApplicationInfoImpl]
    val applicationSettings = ScalaApplicationSettings.getInstance()
    if ((infoImpl.isEAP || infoImpl.isBetaOrRC)
      && applicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS
      && ScalaPluginUpdater.pluginIsRelease) {
      val message = "Please select Scala plugin update channel:" +
        s"""<p/><a href="EAP">EAP</a>\n""" +
        s"""<p/><a href="Release">Release</a>"""
      val updGroupId = "ScalaPluginUpdate"
      val notification = new Notification(updGroupId, "Scala Plugin Update", message, NotificationType.INFORMATION, new NotificationListener {
        def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
          notification.expire()
          applicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS = false
          event.getDescription match {
            case "EAP"     => doUpdatePluginHostsAndCheck(EAP)
            case "Nightly" => doUpdatePluginHostsAndCheck(Nightly)
            case "Release" => doUpdatePluginHostsAndCheck(Release)
            case _         => applicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS = true
          }
        }
      })
      val app: Application = ApplicationManager.getApplication
      app.getMessageBus.syncPublisher(Notifications.TOPIC).register(updGroupId, NotificationDisplayType.STICKY_BALLOON)
      Notifications.Bus.notify(notification)
    }
  }
}
