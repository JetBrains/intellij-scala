package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import psi._
import psi.api.ScalaFile
import com.intellij.navigation.ItemPresentation
import psi.api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable};
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
    /*for (td <- file.immediateTypeDefinitions) {
      children += new ScalaTypeDefinitionStructureViewElement(td)
    }
    for (p <- file.packagings) {
      children += new ScalaPackagingStructureViewElement(p)
    }*/
    for (child <- file.getChildren) {
      child match {
        case td: ScTypeDefinition => {
          children += new ScalaTypeDefinitionStructureViewElement(td)
        }
        case p: ScPackaging => {
          children += new ScalaPackagingStructureViewElement(p)
        }
        case member: ScVariable => {
          for (f <- member.declaredElements)
            children += new ScalaVariableStructureViewElement(f.nameId, false)
        }
        case member: ScValue => {
          for (f <- member.declaredElements)
            children += new ScalaValueStructureViewElement(f.nameId, false)
        }
        case member: ScTypeAlias => {
          children += new ScalaTypeAliasStructureViewElement(member, false)
        }
        case func: ScFunction => {
          children += new ScalaFunctionStructureViewElement(func, false)
        }
        case _ =>
      }
    }
    return children.toArray
  }
}