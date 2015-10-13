
package org.jetbrains.plugins.scala.components

import java.io.{File, IOException}
import java.lang.reflect.Field
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.swing.event.HyperlinkEvent

import com.intellij.ide.plugins._
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification._
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.application.{ApplicationInfo, ApplicationManager}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{DocumentEvent, DocumentAdapter}
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.updateSettings.impl._
import com.intellij.openapi.util.{SystemInfo, BuildNumber, JDOMUtil}
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.Request
import org.jdom.JDOMException
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.pluginBranch._

import scala.xml.transform.{RewriteRule, RuleTransformer}

class InvalidRepoException(what: String) extends Exception(what)

object ScalaPluginUpdater {
  private val LOG = Logger.getInstance(getClass)

  def pluginDescriptor = ScalaPluginVersionVerifier.getPluginDescriptor

  private val scalaPluginId = "1347"

  val baseUrl = "https://plugins.jetbrains.com/plugins/%s/" + scalaPluginId

  private var doneUpdating = false

  // *_OLD versions are for legacy repository format.
  // Need to keep track of them to upgrade to new repository format automatically
  // Remove eventually, when no significant amount of users will have older ones.
  val CASSIOPEIA_OLD    = "cassiopeia_old"
  val FOURTEEN_ONE_OLD  = "14.1_old"
  val FOURTEEN_ONE      = "14.1"

  val knownVersions = Map(
    CASSIOPEIA_OLD   -> Map(
      Release -> "DUMMY",
      EAP     -> "http://www.jetbrains.com/idea/plugins/scala-eap-cassiopeia.xml",
      Nightly -> "http://www.jetbrains.com/idea/plugins/scala-nightly-cassiopeia.xml"
    ),
    FOURTEEN_ONE_OLD -> Map(
      Release -> "DUMMY",
      EAP     -> "http://www.jetbrains.com/idea/plugins/scala-eap-14.1.xml",
      Nightly -> ""
    ),
    FOURTEEN_ONE -> Map(
      Release -> "DUMMY",
      EAP     -> baseUrl.format("eap"),
      Nightly -> baseUrl.format("nightly")
    )
  )

  val currentVersion = FOURTEEN_ONE

  def currentRepo = knownVersions(currentVersion)

  val updGroupId = "ScalaPluginUpdate"
  val GROUP = new NotificationGroup(updGroupId, NotificationDisplayType.STICKY_BALLOON, true)

  // save plugin version before patching to restore it when switching back
  var savedPluginVersion = ""

  private val updateListener = new DocumentAdapter() {
    override def documentChanged(e: DocumentEvent) = {
      val file = FileDocumentManager.getInstance().getFile(e.getDocument)
      if (file != null && file.getFileType == ScalaFileType.SCALA_FILE_TYPE)
        scheduleUpdate()
    }
  }

  @throws(classOf[InvalidRepoException])
  def doUpdatePluginHosts(branch: ScalaApplicationSettings.pluginBranch) = {
    if(currentRepo(branch).isEmpty)
      throw new InvalidRepoException(s"Branch $branch is unavailable for IDEA version $currentVersion")

    // update hack - set plugin version to 0 when downgrading
    // also unpatch it back if user changed mind about downgrading
    if (getScalaPluginBranch.compareTo(branch) > 0) {
      savedPluginVersion = pluginDescriptor.getVersion
      patchPluginVersion("0.0.0")
    } else if (savedPluginVersion.nonEmpty) {
      patchPluginVersion(savedPluginVersion)
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
  def doUpdatePluginHostsAndCheck(branch: ScalaApplicationSettings.pluginBranch) = {
    doUpdatePluginHosts(branch)
    if(UpdateSettings.getInstance().isCheckNeeded) {
      UpdateChecker.updateAndShowResult()
        .doWhenDone(toRunnable(postCheckIdeaCompatibility(branch)))
    }
  }

  def getScalaPluginBranch: ScalaApplicationSettings.pluginBranch = {
    if (ScalaPluginUpdater.pluginIsEap) EAP
    else if (ScalaPluginUpdater.pluginIsNightly) Nightly
    else Release
  }

  def pluginIsEap = {
    val updateSettings = UpdateSettings.getInstance()
    updateSettings.getStoredPluginHosts.contains(currentRepo(EAP))
  }

  def pluginIsNightly = {
    val updateSettings = UpdateSettings.getInstance()
    updateSettings.getStoredPluginHosts.contains(currentRepo(Nightly))
  }

  def pluginIsRelease = !pluginIsEap && !pluginIsNightly

  def uninstallPlugin() = {
    val pluginId: PluginId = pluginDescriptor.getPluginId
    pluginDescriptor.setDeleted(true)

    try {
      PluginInstaller.prepareToUninstall(pluginId)
      val installedPlugins = InstalledPluginsState.getInstance().getInstalledPlugins
      val pluginIdString: String = pluginId.getIdString
      import scala.collection.JavaConversions._
      while (installedPlugins.exists(_.getPluginId.getIdString == pluginIdString)) {
        installedPlugins.remove(pluginIdString)
      }
    }
    catch {
      case e1: IOException => PluginManagerMain.LOG.error(e1)
    }
  }

  def upgradeRepo() = {
    val updateSettings = UpdateSettings.getInstance()
    for {
      (version, repo) <- knownVersions
      if version != currentVersion
    } {
      if (updateSettings.getStoredPluginHosts.contains(repo(EAP))) {
        updateSettings.getStoredPluginHosts.remove(repo(EAP))
        if (!currentRepo(EAP).isEmpty)
          updateSettings.getStoredPluginHosts.add(currentRepo(EAP))
      }
      if (updateSettings.getStoredPluginHosts.contains(repo(Nightly))) {
        updateSettings.getStoredPluginHosts.remove(repo(Nightly))
        if (!currentRepo(Nightly).isEmpty)
          updateSettings.getStoredPluginHosts.add(currentRepo(Nightly))
        else if (!currentRepo(EAP).isEmpty)
          updateSettings.getStoredPluginHosts.add(currentRepo(EAP))
      }
    }
  }

  def postCheckIdeaCompatibility(branch: ScalaApplicationSettings.pluginBranch) = {
    import scala.xml._
    val infoImpl = ApplicationInfo.getInstance().asInstanceOf[ApplicationInfoImpl]
    val localBuildNumber = infoImpl.getBuild
    val url = branch match {
      case Release => None
      case EAP     => Some(currentRepo(EAP))
      case Nightly => Some(currentRepo(Nightly))
    }

    url.foreach(u => invokeLater {
      try {
        val resp = XML.load(u)
        val text = ((resp \\ "idea-plugin").head \ "idea-version" \ "@since-build").text
        val remoteBuildNumber = BuildNumber.fromString(text)
        if (localBuildNumber.compareTo(remoteBuildNumber) < 0)
          suggestIdeaUpdate(branch.toString, text)
      }
      catch {
        case e: Throwable => LOG.info("Failed to check plugin compatibility", e)
      }
    })
  }

  def postCheckIdeaCompatibility(): Unit = postCheckIdeaCompatibility(getScalaPluginBranch)

  private def suggestIdeaUpdate(branch: String, suggestedVersion: String) = {
    val infoImpl = ApplicationInfo.getInstance().asInstanceOf[ApplicationInfoImpl]
    val appSettings = ScalaApplicationSettings.getInstance()
    def getPlatformUpdateResult = {
      val a = ApplicationInfoEx.getInstanceEx.getUpdateUrls.getCheckingUrl
      val info = HttpRequests.request(a).connect(new HttpRequests.RequestProcessor[Option[UpdatesInfo]] {
        def process(request: HttpRequests.Request) = {
          try { Some(new UpdatesInfo(JDOMUtil.loadDocument(request.getInputStream).detachRootElement)) }
          catch { case e: JDOMException => LOG.info(e); None }
        }
      })
      if(info.isDefined) {
        val strategy = new UpdateStrategy(infoImpl.getMajorVersion.toInt, infoImpl.getBuild, info.get, UpdateSettings.getInstance())
        Some(strategy.checkForUpdates())
      } else None
    }
    def isUpToDatePlatform(result: CheckForUpdateResult) = result.getUpdatedChannel.getLatestBuild.getNumber.compareTo(infoImpl.getBuild) <= 0
    def isBetaOrEAPPlatform = infoImpl.isEAP || infoImpl.isBetaOrRC
    val notification = getPlatformUpdateResult match {
      case Some(result) if isUpToDatePlatform(result) && !isBetaOrEAPPlatform && appSettings.ASK_PLATFORM_UPDATE => // platform is up to date - suggest eap
        val message = s"Your IDEA is outdated to use with $branch branch.<br/>Would you like to switch IDEA channel to EAP?" +
          s"""<p/><a href="Yes">Yes</a>\n""" +
          s"""<p/><a href="No">Not now</a>""" +
          s"""<p/><a href="Ignore">Ignore this update</a>"""
        Some(GROUP.createNotification(
          "Scala Plugin Update Failed",
          message,
          NotificationType.WARNING,
          new NotificationListener {
            override def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent): Unit = {
              notification.expire()
              event.getDescription match {
                case "No" => // do nothing, will ask next time
                case "Yes" => UpdateSettings.getInstance().setUpdateChannelType("eap")
                case "Ignore" => appSettings.ASK_PLATFORM_UPDATE = false
              }
            }
          }
        ))
      case Some(result) => Some(GROUP.createNotification(
            s"Your IDEA is outdated to use with Scala plugin $branch branch.<br/>" +
            s"Please update IDEA to at least $suggestedVersion to use latest Scala plugin.",
            NotificationType.WARNING)
      )
      case None => None
    }
    notification.foreach(Notifications.Bus.notify)
  }

  private def scheduleUpdate(): Unit = {
    val key = "scala.last.updated"
    val lastUpdateTime = PropertiesComponent.getInstance().getOrInitLong(key , 0)
    EditorFactory.getInstance().getEventMulticaster.removeDocumentListener(updateListener)
    if (lastUpdateTime == 0L || System.currentTimeMillis() - lastUpdateTime > TimeUnit.DAYS.toMillis(1)) {
      ApplicationManager.getApplication.executeOnPooledThread(new Runnable {
        override def run() = {
          val buildNumber = ApplicationInfo.getInstance().getBuild.asString()
          val pluginVersion = pluginDescriptor.getVersion
          val os = URLEncoder.encode(SystemInfo.OS_NAME, CharsetToolkit.UTF8)
          val uid = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
          val url = s"https://plugins.jetbrains.com/plugins/list?pluginId=$scalaPluginId&build=$buildNumber&pluginVersion=$pluginVersion&os=$os&uuid=a$uid"
          PropertiesComponent.getInstance().setValue(key, System.currentTimeMillis().toString)
          doneUpdating = true
          try {
            HttpRequests.request(url).connect(new HttpRequests.RequestProcessor[Unit] {
              override def process(request: Request) = JDOMUtil.load(request.getReader())
            })
          } catch {
            case e: Throwable => LOG.warn(e)
          }
        }
      })
    }
  }

  def setupReporter() = {
    import com.intellij.openapi.editor.EditorFactory
    EditorFactory.getInstance().getEventMulticaster.addDocumentListener(updateListener)
  }

  // this hack uses fake plugin.xml deserialization to downgrade plugin version
  // at least it's not using reflection
  def patchPluginVersion(newVersion: String) = {
    import scala.xml._
    val versionPatcher = new RewriteRule {
      override def transform(n: Node): NodeSeq = n match {
        case <version>{_}</version> => <version>{newVersion}</version>
        case <include/> => NodeSeq.Empty // relative path includes break temp file parsing
        case other => other
      }
    }
    val stream = getClass.getClassLoader.getResource("META-INF/plugin.xml").openStream()
    val document = new RuleTransformer(versionPatcher).transform(XML.load(stream))
    val tempFile = File.createTempFile("plugin", "xml")
    XML.save(tempFile.getAbsolutePath, document.head)
    pluginDescriptor.readExternal(tempFile.toURI.toURL)
    tempFile.delete()
  }

  @deprecated
  def patchPluginVersionReflection() = {
    // crime of reflection goes below - workaround until force updating is available
    try {
      val hack: Field = classOf[IdeaPluginDescriptorImpl].getDeclaredField("myVersion")
      hack.setAccessible(true)
      hack.set(pluginDescriptor, "0.0.0")
    }
    catch {
      case _: NoSuchFieldException | _: IllegalAccessException  =>
        Notifications.Bus.notify(new Notification(updGroupId, "Scala Plugin Update", "Please remove and reinstall Scala plugin to finish downgrading", NotificationType.INFORMATION))
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
      Notifications.Bus.notify(notification)
    }
  }

  def invokeLater(f: => Unit) = ApplicationManager.getApplication.executeOnPooledThread(toRunnable(f))

  def toRunnable(f: => Unit) = new Runnable { override def run(): Unit = f }
}
