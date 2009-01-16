package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import psi._
import psi.api.ScalaFile;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFileStructureViewElement(file: ScalaFile) extends ScalaStructureViewElement(file, false) {
  def getPresentation(): ItemPresentation = {
    return new ScalaFileItemPresentation(file);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    for (td <- file.immediateTypeDefinitions) {
      children += new ScalaTypeDefinitionStructureViewElement(td)
    }
    for (p <- file.packagings) {
      children += new ScalaPackagingStructureViewElement(p)
    }
    return children.toArray
  }
}