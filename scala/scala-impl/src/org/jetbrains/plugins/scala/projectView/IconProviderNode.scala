package org.jetbrains.plugins.scala
package projectView

import java.util.Collections.emptyList

import com.intellij.ide.projectView.PresentationData
import javax.swing.Icon

private[projectView] trait IconProviderNode {

  def icon(flags: Int): Icon

  //noinspection TypeAnnotation
  final def emptyNodesList = emptyList[Node]()

  final def setIcon(data: PresentationData): Unit = {
    data.setIcon(icon(0))
  }
}
