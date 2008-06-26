package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFileStructureViewElement(file: ScalaFile) extends ScalaStructureViewElement(file) {
  def getPresentation(): ItemPresentation = {
    return new ScalaFileItemPresentation(file);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    for (topStatement <- file.getTopStatements) {
      topStatement match {
        case td: ScTypeDefinition => {
          children += new ScalaTypeDefinitionStructureViewElement(td)
        }
        case pack: ScPackaging => {
          children += new ScalaPackagingStructureViewElement(pack)
        }
        case _ =>
      }
    }
    return children.toArray
  }
}