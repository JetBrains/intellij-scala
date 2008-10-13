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

class ScalaTypeDefinitionStructureViewElement(private val element: ScTypeDefinition) extends ScalaStructureViewElement(element) {

  def getPresentation(): ItemPresentation = {
    return new ScalaTypeDefinitionItemPresentation(element);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    val members = element.asInstanceOf[ScTypeDefinition].members
    for (member <- members) {
      member match {
        case func: ScFunction => {
          children += new ScalaFunctionStructureViewElement(func, false)
        }
        case constr: ScPrimaryConstructor => {
          children += new ScalaPrimaryConstructorStructureViewElement(constr)
        }
        case member: ScVariable => {
          for (f <- member.declaredElements)
            children += new ScalaVariableStructureViewElement(f.nameId)
        }
        case member: ScValue => {
          for (f <- member.declaredElements)
            children += new ScalaValueStructureViewElement(f.nameId)
        }
        case member: ScTypeAlias => {
          children += new ScalaTypeAliasStructureViewElement(member)
        }
        case _ =>
      }
    }

    for (typeDef <- element.typeDefinitions)
      children += new ScalaTypeDefinitionStructureViewElement(typeDef)
    return children.toArray
  }
}