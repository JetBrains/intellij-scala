package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._

import _root_.scala.collection.mutable._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionStructureViewElement(private val element: ScalaPsiElement, isInherited: Boolean) extends ScalaStructureViewElement(element) {

  def getPresentation(): ItemPresentation = {
    return new ScalaFunctionItemPresentation(myElement.asInstanceOf[ScFunction], isInherited);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    for (field <- myElement.asInstanceOf[ScFunction].getFunctionsAndTypeDefs) {
      field match {
        case _: ScTypeDefinition => {
          children += new ScalaTypeDefinitionStructureViewElement(field)
        }
        case _: ScFunction => {
          children += new ScalaFunctionStructureViewElement (field, false)
        }
        case _ =>
      }
    }
    return children.toArray
  }
}