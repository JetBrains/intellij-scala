package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.projectView.PresentationData
import javax.swing.Icon

private[projectView] trait IconProviderNode {

  def icon(flags: Int): Icon

  final def setIcon(data: PresentationData): Unit = {
    data.setIcon(icon(0))
  }
}
