package org.jetbrains.plugins.scala.components

import com.intellij.ide.plugins.{org => _, _}
import com.intellij.notification._
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.application.{ApplicationInfo, ApplicationManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl._
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.util.{BuildNumber, JDOMUtil}
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.util.io.HttpRequests
import org.jdom.JDOMException
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.pluginBranch
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.pluginBranch._
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.plugins.scala.{ScalaBundle, extensions}

import scala.xml.XML

class InvalidRepoException(what: String) extends Exception(what)

//noinspection UnstableApiUsage,ApiStatus
object ScalaPluginUpdater {
  private val LOG = Logger.getInstance(getClass)
  private val scalaPluginId = "1347"
  private val baseUrl: String = "https://plugins.jetbrains.com/plugins/%s/" + scalaPluginId
  private def pluginDescriptor: IdeaPluginDescriptorImpl = ScalaPluginVersionVerifier.getPluginDescriptor

  private def currentRepo: Map[pluginBranch, String] = Map(
    Release -> "DUMMY",
    EAP     -> baseUrl.format("eap"),
    Nightly -> baseUrl.format("nightly")
  )

  private def NotificationGroup = ScalaNotificationGroups.scalaPluginUpdater

  // save plugin version before patching to restore it when switching back
  private var savedPluginVersion = ""

  @throws(classOf[InvalidRepoException])
  def doUpdatePluginHosts(branch: ScalaApplicationSettings.pluginBranch, descriptor: IdeaPluginDescriptorImpl): AnyVal = {
    if (currentRepo(branch).isEmpty)
      throw new InvalidRepoException(s"Branch $branch is unavailable")

    // update hack - set plugin version to 0 when downgrading
    // also unpatch it back if user changed mind about downgrading
    if (getScalaPluginBranch.compareTo(branch) > 0) {
      savedPluginVersion = descriptor.getVersion
      patchPluginVersion("0.0.0", descriptor)
    } else if (savedPluginVersion.nonEmpty) {
      patchPluginVersion(savedPluginVersion, descriptor)
      savedPluginVersion = ""
    }

    val updateSettings = UpdateSettings.getInstance()
    updateSettings.getStoredPluginHosts.remove(currentRepo(EAP))
    updateSettings.getStoredPluginHosts.remove(currentRepo(Nightly))

    branch match {
      case Release => // leave default plugin repository
      case EAP     => updateSettings.getStoredPluginHosts.add(currentRepo(EAP))
      case Nightly => updateSettings.getStoredPluginHosts.add(currentRepo(Nightly))
    }
  }

  @throws(classOf[InvalidRepoException])
  def doUpdatePluginHostsAndCheck(branch: ScalaApplicationSettings.pluginBranch): Any = {
    doUpdatePluginHosts(branch, pluginDescriptor)
    if(UpdateSettings.getInstance().isCheckNeeded) {
      UpdateChecker.updateAndShowResult()
        .doWhenDone(() => postCheckIdeaCompatibility(branch))
    }
  }

  def getScalaPluginBranch: ScalaApplicationSettings.pluginBranch = {
    if (ScalaPluginUpdater.pluginIsEap) EAP
    else if (ScalaPluginUpdater.pluginIsNightly) Nightly
    else Release
  }

  private def pluginIsEap: Boolean = {
    val updateSettings = UpdateSettings.getInstance()
    updateSettings.getStoredPluginHosts.contains(currentRepo(EAP))
  }

  def pluginIsNightly: Boolean = {
    val updateSettings = UpdateSettings.getInstance()
    updateSettings.getStoredPluginHosts.contains(currentRepo(Nightly))
  }

  private def pluginIsRelease: Boolean = !pluginIsEap && !pluginIsNightly

  def postCheckIdeaCompatibility(branch: ScalaApplicationSettings.pluginBranch): Unit = {
    val infoImpl = ApplicationInfo.getInstance()
    val localBuildNumber = infoImpl.getBuild
    val url = branch match {
      case Release => None
      case EAP     => Some(currentRepo(EAP))
      case Nightly => Some(currentRepo(Nightly))
    }

    url.foreach(u => extensions.executeOnPooledThread {
      try {
        val factory = javax.xml.parsers.SAXParserFactory.newInstance()
        // disable DTD validation
        factory.setValidating(false)
        factory.setFeature("http://xml.org/sax/features/validation", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val resp = XML.withSAXParser(factory.newSAXParser).load(u)
        val text = ((resp \\ "idea-plugin").head \ "idea-version" \ "@since-build").text
        val remoteBuildNumber = BuildNumber.fromString(text)
        if (localBuildNumber.compareTo(remoteBuildNumber) < 0)
          suggestIdeaUpdate(branch.toString, text)
      }
      catch {
        case e: Throwable => LOG.warn("Failed to check plugin compatibility", e)
      }
    })
  }

  def postCheckIdeaCompatibility(): Unit = postCheckIdeaCompatibility(getScalaPluginBranch)

  private def suggestIdeaUpdate(branch: String, suggestedVersion: String): Unit = {
    val infoImpl = ApplicationInfo.getInstance()
    val appSettings = ScalaApplicationSettings.getInstance()

    if (!appSettings.ASK_PLATFORM_UPDATE)
      return

    def createPlatformUpdateSuggestPopup(): Notification = {
      NotificationGroup.createNotification(
        ScalaBundle.message("idea.is.outdated.please.update", branch, suggestedVersion),
        NotificationType.WARNING
      )
    }

    def getPlatformUpdateResult: Option[PlatformUpdates] = {
      val url = ExternalProductResourceUrls.getInstance.getUpdateMetadataUrl
      if (url == null)
        return None

      val info: Option[Product] = HttpRequests.request(url).connect { request =>
        val productCode = ApplicationInfo.getInstance().getBuild.getProductCode
        try   { Option(UpdateData.parseUpdateData(JDOMUtil.load(request.getInputStream), productCode)) }
        catch { case e: JDOMException => LOG.info(e); None }
      }

      info.map(new UpdateStrategy(infoImpl.getBuild, _, UpdateSettings.getInstance()).checkForUpdates())
    }

    val notification = getPlatformUpdateResult match {
      case Some(_: PlatformUpdates.Loaded) =>
        Some(createPlatformUpdateSuggestPopup())
      case _ => None
    }

    notification.foreach(Notifications.Bus.notify)
  }

  // this hack uses reflection to downgrade plugin version
  def patchPluginVersion(newVersion: String, descriptor: IdeaPluginDescriptorImpl): Unit = {
    val versionField = classOf[IdeaPluginDescriptorImpl].getDeclaredField("version")
    versionField.setAccessible(true)
    versionField.set(descriptor, newVersion)
    versionField.setAccessible(false)
  }

  def askUpdatePluginBranchIfNeeded(): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode)
      return

    val ideaApplicationInfo = ApplicationInfo.getInstance()
    val scalaApplicationSettings = ScalaApplicationSettings.getInstance()

    val isEapIdeaInstallation = ideaApplicationInfo.isEAP
    val showSelectScalaPluginUpdateChannelNotification =
      isEapIdeaInstallation &&
        scalaApplicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS &&
        ScalaPluginUpdater.pluginIsRelease

    if (showSelectScalaPluginUpdateChannelNotification) {
      val notification = NotificationGroup
        .createNotification(
          ScalaBundle.message("scala.plugin.update"),
          ScalaBundle.message("please.select.scala.plugin.update.channel"),
          NotificationType.INFORMATION
        )

      class SelectChannelAction(
        @ActionText channelDisplayName: String,
        pluginBranch: pluginBranch
      ) extends AnAction(channelDisplayName) {
        override def actionPerformed(e: AnActionEvent): Unit = {
          scalaApplicationSettings.ASK_USE_LATEST_PLUGIN_BUILDS = false
          doUpdatePluginHostsAndCheck(pluginBranch)
          notification.expire()
        }

        override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT
      }

      notification
        .addAction(new SelectChannelAction(ScalaBundle.message("channel.stable.releases"), Release))
        .addAction(new SelectChannelAction(ScalaBundle.message("channel.early.access.program"), EAP))
        .addAction(new SelectChannelAction(ScalaBundle.message("channel.nightly.builds"), Nightly))

      val project = ProjectManager.getInstance().getOpenProjects.headOption.orNull
      notification.notify(project)
    }
  }

}
