package org.jetbrains.plugins.scala.lang.psi.impl.top.templates

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 17:54:19
 */

/*************** templates **************/
abstract class Template(node: ASTNode) extends ScalaPsiElementImpl (node) {

  def getTemplateParents = getChild(ScalaElementTypes.TEMPLATE_PARENTS).asInstanceOf[ScTemplateParents]

  def getTemplateBody = getChild(ScalaElementTypes.TEMPLATE_BODY).asInstanceOf[ScTemplateBody]

  override def toString: String = "template"
}

case class ScTopDefTemplate(node: ASTNode) extends Template (node) {
  override def toString: String = "top definition" + " " + super.toString
}

/*********** class **************/

case class ScRequiresBlock(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "requires block"
}

case class ScExtendsBlock(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "extends block"
}


case class ScTemplate(node: ASTNode) extends Template (node) {
  override def toString: String = super.toString
}

/**************** parents ****************/

class Parents(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "parents"
}

case class ScTemplateParents(node: ASTNode) extends Parents (node) {
  override def toString: String = "template" + " " + super.toString
}

case class ScMixinParents(node: ASTNode) extends Parents (node) {
  override def toString: String = "mixin" + " " + super.toString
}

/***************** body *******************/
case class ScTemplateBody(node: ASTNode) extends ScalaPsiElementImpl (node) with BlockedIndent{
  override def toString: String = "template body"
}
