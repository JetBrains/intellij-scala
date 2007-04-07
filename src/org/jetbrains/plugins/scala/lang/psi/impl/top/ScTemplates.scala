package org.jetbrains.plugins.scala.lang.psi.impl.top.templates

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.scope._
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.resolve.processors._
import org.jetbrains.plugins.scala.lang.psi.containers._

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

/**
*    Implements logic with body of classes, traits and objects
*
*/
case class ScTemplateBody(node: ASTNode) extends ScalaPsiElementImpl (node) with BlockedIndent with Importable with LocalContainer{
  override def toString: String = "template body"

  def getTypes = childrenOfType[ScalaPsiElementImpl](ScalaElementTypes.TMPL_OR_TYPE_BIT_SET)

  override def processDeclarations(processor: PsiScopeProcessor,
          substitutor: PsiSubstitutor,
          lastParent: PsiElement,
          place: PsiElement) : Boolean = {                         

    import org.jetbrains.plugins.scala.lang.resolve.processors._
    if (processor.isInstanceOf[ScalaClassResolveProcessor]) { // GetClasses
      this.canBeObject = processor.asInstanceOf[ScalaClassResolveProcessor].canBeObject
      this.offset = processor.asInstanceOf[ScalaClassResolveProcessor].offset
      getClazz(getTypes, processor, substitutor)
    } else if (processor.isInstanceOf[ScalaLocalVariableResolveProcessor]){ // Get Local variables
        this.varOffset = processor.asInstanceOf[ScalaLocalVariableResolveProcessor].offset
      getVariable(processor, substitutor)
    } else true

  }
}


