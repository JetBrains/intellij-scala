package org.jetbrains.plugins.scala
package projectView

import java.util.Collections.emptyList

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.ValidateableNode
import com.intellij.openapi.util.Iconable

private[projectView] trait IconableNode extends ValidateableNode with Iconable {

  //noinspection TypeAnnotation
  final def emptyNodesList = emptyList[Node]()

  final def setIcon(data: PresentationData): Unit = {
    data.setIcon(getIcon(0))
  }
}
