package org.jetbrains.plugins.scala.help

import com.intellij.openapi.help.WebHelpProvider

class ScalaWebHelpProvider extends WebHelpProvider {
  import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider._

  override def getHelpPageUrl(helpTopicId: String): String =
    if (helpTopicId.startsWith(HelpPrefix))
      s"$HelpUrl${helpTopicId.stripPrefix(HelpPrefix)}"
    else
      null

  override def getHelpTopicPrefix: String = HelpPrefix
}

object ScalaWebHelpProvider {
  val HelpPrefix = "org.jetbrains.plugins.scala."
  val HelpUrl = "https://www.jetbrains.com/help/idea/"
}
