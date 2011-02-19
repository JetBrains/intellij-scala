package org.jetbrains.plugins.scala

import java.net.URI
import java.awt.Desktop
import com.intellij.openapi.ui.Messages

/**
 * Pavel Fatin
 */

object DesktopUtils {
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