package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

import _root_.scala.collection.mutable._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionStructureViewElement(clazz: ScTypeDefinition) extends ScalaStructureViewElement(clazz, false) {

  def getPresentation: ItemPresentation = {
    new ScalaTypeDefinitionItemPresentation(clazz)
  }

  def getChildren: Array[TreeElement] = {
    val children = new ArrayBuffer[TreeElement]
    val members = clazz.members
    for (member <- members) {
      member match {
        case func: ScFunction =>
          children += new ScalaFunctionStructureViewElement(func, false)
// TODO format parameters as Foo(...), add val and var members
//        case constr: ScPrimaryConstructor =>
//          children += new ScalaPrimaryConstructorStructureViewElement(constr)
        case member: ScVariable =>
          for (f <- member.declaredElements)
            children += new ScalaVariableStructureViewElement(f, false)
        case member: ScValue =>
          for (f <- member.declaredElements)
            children += new ScalaValueStructureViewElement(f, false)
        case member: ScTypeAlias =>
          children += new ScalaTypeAliasStructureViewElement(member, false)
        case _ =>
      }
    }
    for (typeDef <- clazz.typeDefinitions)
      children += new ScalaTypeDefinitionStructureViewElement(typeDef)
    children.toArray
  }
}