package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import api.toplevel.typedef.ScObject
import api.expr.ScNewTemplateDefinition
import com.intellij.lang.ASTNode
import com.intellij.psi.JavaPsiFacade

import psi.ScalaPsiElementImpl
import api.toplevel.templates._
import psi.types._
import _root_.scala.collection.mutable.ArrayBuffer

/**
 * @author AlexanderPodkhalyuzin
* Date: 20.02.2008
 */

class ScExtendsBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExtendsBlock {

  override def toString: String = "ExtendsBlock"

  def templateBody: Option[ScTemplateBody] = findChild(classOf[ScTemplateBody])

  def empty = getNode.getFirstChildNode == null

  def superTypes(): Seq[ScType] = {
    val buffer = new ArrayBuffer[ScType]
    templateParents match {
      case None =>
      case Some(parents) => {
        parents match {
          case classParents: ScClassParents =>
            classParents.constructor match {
              case None => ()
              case Some(c) => buffer += c.typeElement.getType
            }
          case _ =>
        }
        buffer ++= (parents.typeElements map {
          typeElement => typeElement.getType
        }).toArray
      }
    }
    val so = scalaObject()
    if (so != null) buffer += so
    buffer.toArray
  }

  private def scalaObject() = {
    val so = JavaPsiFacade.getInstance(getProject).findClass("scala.ScalaObject")
    val desType = if (so != null) new ScDesignatorType(so) else null
    getParent match { //to prevent SOE during resolve ScalaOject through Predef <: ScalaObject
      case o: ScObject if !("scala.Predef".equals(o.getQualifiedName)) => desType
      case _ => null
    }
  }

  def isAnonymousClass: Boolean = {
    getParent match {
      case _: ScNewTemplateDefinition =>
      case _ => return false
    }
    templateBody match {
      case Some(x) => return true
      case None => return false
    }
  }
}