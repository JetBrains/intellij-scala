package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.util.treeView.smartTree.{NodeProvider, TreeElement}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewModelProvider
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

//noinspection ApiStatus
private final class TestStructureViewModelProvider extends ScalaStructureViewModelProvider {
  override def nodeProvidersFor(rootElement: ScalaFile): Seq[NodeProvider[_ <: TreeElement]] = {
    if (rootElement.getFileType == ScalaFileType.INSTANCE)
      Seq(new TestNodeProvider())
    else
      Seq.empty
  }
}
