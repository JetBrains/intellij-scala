package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.types._
import _root_.scala.collection.mutable.ArrayBuffer

import com.intellij.psi.JavaPsiFacade

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScExtendsBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExtendsBlock {

  override def toString: String = "ExtendsBlock"

  def templateBody: Option[ScTemplateBody] = findChild(classOf[ScTemplateBody])

  def empty = getNode.getFirstChildNode == null

  def superTypes(): Seq[ScType] = {
    val buffer = new ArrayBuffer[ScType]
    templateParents match {
      case None => ()
      case Some(parents) => {
        parents.constructor match {
          case None => ()
          case Some(c) => buffer += c.typeElement.getType
        }
        buffer ++= (parents.traits map {
          typeElement => typeElement.getType
        }).toArray
      }
    }
    val so = scalaObject()
    if (so != null) buffer += so
    buffer.toArray
  }

  def scalaObject() = {
    val so = JavaPsiFacade.getInstance(getProject).findClass("scala.ScalaObject")
    if (so != null) new ScParameterizedType(so, ScSubstitutor.empty) else null
  }
}