package org.jetbrains.plugins.scala.testingSupport

import com.intellij.ide.util.treeView.smartTree.{NodeProvider, TreeElement}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.structureView.ScalaStructureViewModel
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

class TestStructureViewModel(myRootElement: ScalaFile, console: Option[ScalaLanguageConsole] = None)
  extends ScalaStructureViewModel(myRootElement, console) {
  override def getNodeProviders: java.util.Collection[NodeProvider[_ <: TreeElement]] = {
    if (myRootElement.getFileType == ScalaFileType.INSTANCE)
      java.util.List.of(new TestNodeProvider())
    else
      java.util.Collections.emptyList()
  }
}
