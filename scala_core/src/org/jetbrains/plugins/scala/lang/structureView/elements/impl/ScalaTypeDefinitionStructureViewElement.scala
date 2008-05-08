package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionStructureViewElement(private val element: ScalaPsiElement) extends ScalaStructureViewElement (element) {
  def getPresentation(): ItemPresentation = {
    return new ScalaTypeDefinitionItemPresentation (myElement.asInstanceOf[ScTypeDefinition]);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    for (field <- myElement.asInstanceOf[ScTypeDefinition].getFieldsAndMethods) {
      field match {
        case _: ScTypeDefinition => {
          children += new ScalaTypeDefinitionStructureViewElement(field)
        }
        case _: ScFunction => {
          children += new ScalaFunctionStructureViewElement (field, false)
        }
        case field: ScVariable => {
          for (f <- field.getIdentifierNodes)
            children += new ScalaVariableStructureViewElement(f)
        }
        case field: ScValue => {
          for (f <- field.getIdentifierNodes)
            children += new ScalaValueStructureViewElement(f)
        }
        case _ =>
      }
    }
    return children.toArray
  }
}