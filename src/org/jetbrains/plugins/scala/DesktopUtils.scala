package org.jetbrains.plugins.scala

import java.awt.Desktop
import com.intellij.openapi.ui.Messages
import java.net.{URL, URI}

/**
 * Pavel Fatin
 */

object DesktopUtils {
  def browse(url: URL) {
    browse(url.toExternalForm)
  }

  def browse(url: String) {
    val supported = Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)

    if(supported)
      Desktop.getDesktop.browse(new URI(url))
    else
      Messages.showWarningDialog(
        "Unable to launch a web browser, please open: %s".format(url),
        "Problem opening web page")
  }
}