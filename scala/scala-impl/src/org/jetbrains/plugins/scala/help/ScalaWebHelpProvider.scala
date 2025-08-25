package org.jetbrains.plugins.scala.help

import com.intellij.openapi.application.{ApplicationInfo, ApplicationManager}
import com.intellij.openapi.help.{KeymapHelpIdPresenter, WebHelpProvider}
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.util.{Url, Urls}
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider._

import scala.jdk.CollectionConverters.MapHasAsJava

class ScalaWebHelpProvider extends WebHelpProvider {

  /**
   * @param helpTopicId example: `org.jetbrains.plugins.scala.sbt-support.html`
   */
  override def getHelpPageUrl(helpTopicId: String): String = {
    if (helpTopicId.startsWith(HelpPrefix)) {
      val relativePath = helpTopicId.stripPrefix(HelpPrefix)

      // ensure help is version-specific
      // (for example, EAP IDE should open the EAP versions of the page, older versions should open older versions)
      val url = BaseHelpUrl
        .resolve(s"${ApplicationInfo.getInstance.getShortVersion}/")
        .resolve(relativePath)

      val urlWithKeymap = addActiveKeyMapParameter(url)
      urlWithKeymap.toExternalForm
    }
    else null
  }

  /**
   * Copied from [[com.intellij.help.impl.HelpManagerImpl#getHelpUrl]].<
   * I wonder why it's not added to all links by default?
   */
  private def addActiveKeyMapParameter(url: Url): Url = {
    val knownActiveKeymap = getKnownActiveKeyMap
    if (knownActiveKeymap != null) {
      val keymapId = ApplicationManager.getApplication.getService(classOf[KeymapHelpIdPresenter]).getKeymapIdForHelp(knownActiveKeymap)
      url.addParameters(Map("keymap" -> keymapId).asJava)
    }
    else url
  }

  /**
   * @return the keymap that is currently active and is among predefined IDE keymaps.<br>
   *         If the user has a custom keymap, we need to show the predefined keymap it was inherited from
   */
  private def getKnownActiveKeyMap = {
    val activeKeymap = KeymapManagerEx.getInstanceEx.getActiveKeymap
    if (activeKeymap.canModify)
      activeKeymap.getParent
    else
      activeKeymap
  }

  override def getHelpTopicPrefix: String = HelpPrefix
}

object ScalaWebHelpProvider {
  val HelpPrefix = "org.jetbrains.plugins.scala."

  // Note, this is a copy from new IntelliJIdeaExternalResourceUrls().getBaseWebHelpUrl.
  // We can't reuse it because for some reason a "NoClassDefFoundError" is thrown.
  // It might be the issue only in the Dev IDEA instance caused by imperfect classpath initialising, different from prod.
  // But I am not 100% sure, anyway, just hardcoding the URL here for now. It worked for years anyway
  private val BaseHelpUrl =  Urls.newFromEncoded("https://www.jetbrains.com/help/idea/")
}
