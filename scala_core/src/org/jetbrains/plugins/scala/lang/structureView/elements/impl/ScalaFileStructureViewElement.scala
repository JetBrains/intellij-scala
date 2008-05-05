package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import _root_.scala.collection.mutable._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFileStructureViewElement(private val element: ScalaPsiElement) extends ScalaStructureViewElement(element) {
  def getPresentation(): ItemPresentation = {
    return new ScalaFileItemPresentation(myElement.asInstanceOf[ScalaFile]);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    for (topStatement <- myElement.asInstanceOf[ScalaFile].getTopStatements) {
      topStatement match {
        case _: ScTypeDefinition => {
          children += new ScalaTypeDefinitionStructureViewElement(topStatement)
        }
        case _ =>
      }
    }
    return children.toArray
  }
}