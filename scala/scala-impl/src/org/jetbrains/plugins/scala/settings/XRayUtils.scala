package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.Nls

import java.awt.event.KeyEvent

object XRayUtils {

  private def xRayActionKeyModifier: Int =
    if (SystemInfoRt.isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL

  /**
   * Return string OS-friendly representation of key text.<br>
   * For example on Mac it will return
   *  - "⌘" instead of "Cmd"
   *  - "⌥" instead of "Alt"
   */
  //noinspection ReferencePassedToNls
  @Nls def xRayActionKeyText: String =
    KeyEvent.getKeyText(xRayActionKeyModifier)
}
