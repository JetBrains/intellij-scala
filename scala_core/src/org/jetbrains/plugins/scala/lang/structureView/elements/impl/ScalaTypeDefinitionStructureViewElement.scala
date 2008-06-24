package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionStructureViewElement(private val element: ScalaPsiElement) extends ScalaStructureViewElement(element) {

  def getPresentation(): ItemPresentation = {
    return new ScalaTypeDefinitionItemPresentation(myElement.asInstanceOf[ScTypeDefinition]);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]

    for (member <- element.asInstanceOf[ScTypeDefinition].members) {
      member match {
        case _: ScFunction => {
          children += new ScalaFunctionStructureViewElement (member, false)
        }
        case _: ScPrimaryConstructor => {
          children += new ScalaPrimaryConstructorStructureViewElement (member)
        }
        case member: ScVariable => {
          for (f <- member.declaredElements)
                  children += new ScalaVariableStructureViewElement (f.nameId)
        }
        case member: ScValue => {
          for (f <- member.declaredElements)
                  children += new ScalaValueStructureViewElement (f.nameId)
        }
        case _ =>
      }
    }

    for (typeDef <- element.asInstanceOf[ScTypeDefinition].typeDefinitions)
      children += new ScalaTypeDefinitionStructureViewElement(typeDef)
    return children.toArray
  }
}