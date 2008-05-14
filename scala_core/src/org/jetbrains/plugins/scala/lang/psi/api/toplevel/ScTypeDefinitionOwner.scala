package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import _root_.scala.collection.mutable._

/**
 * Trait that implements logic by some type definitions aggregation
 *
 * @author ilyas
 */

trait ScTypeDefinitionOwner extends ScalaPsiElement {

  /**
  * @return Type definitions inside current packaging
  */
  def getTypeDefinitions: List[ScTypeDefinition] = {
    val children = childrenOfType[ScTypeDefinition](TokenSets.TMPL_OR_PACKAGING_DEF_BIT_SET).toList.toArray
    val list = new ListBuffer[ScTypeDefinition]
    for (child <- getChildren) {
      child match {
        case f: ScFunctionDefinition => {
          list.appendAll(f.getTypeDefinitions)
        }
        case p: ScPackaging => list.appendAll(p.getTypeDefinitions)
        case t: ScTypeDefinition => {
          list.append(t)
          list.appendAll(t.getTypeDefinitions)
        }
        case _ =>
      }
    }
    if (this.isInstanceOf[ScTypeDefinition]) {
      for (child <- this.asInstanceOf[ScTypeDefinition].getExtendsBlock.getTemplateBody.getChildren) {
        child match {
          case f: ScFunctionDefinition => {
            list.appendAll(f.getTypeDefinitions)
          }
          case p: ScPackaging => list.appendAll(p.getTypeDefinitions)
          case t: ScTypeDefinition => {
            list.append(t)
            list.appendAll(t.getTypeDefinitions)
          }
          case _ =>
        }
      }
    }
    if (this.isInstanceOf[ScFunctionDefinition]) {
      for (child <- this.asInstanceOf[ScFunctionDefinition].getBody.getChildren) {
        child match {
          case f: ScFunctionDefinition => {
            list.appendAll(f.getTypeDefinitions)
          }
          case p: ScPackaging => list.appendAll(p.getTypeDefinitions)
          case t: ScTypeDefinition => {
            list.append(t)
            list.appendAll(t.getTypeDefinitions)
          }
          case _ =>
        }
      }
    }
    val t = list.length
    return list.toList
  }

  def getTypeDefinitionsArray: Array[ScTypeDefinition] = getTypeDefinitions.toArray[ScTypeDefinition]

}