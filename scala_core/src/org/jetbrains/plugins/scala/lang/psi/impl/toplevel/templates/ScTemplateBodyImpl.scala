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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:04
*/

class ScTemplateBodyImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTemplateBody{
  override def toString: String = "ScTemplateBody"

  def getTypes = {
    childrenOfType[ScalaPsiElementImpl](TokenSets.TMPL_OR_TYPE_BIT_SET)
  }

  def members = findChildrenByClass(classOf[ScMember])

  def typeDefinitions: Seq[ScTypeDefinition] = findChildrenByClass(classOf[ScTypeDefinition])
  //

  /*def getTemplateStatements = {
    val children = childrenOfType[ScTemplateStatement](ScalaElementTypes.TMPL_STMT_BIT_SET)
    children
  } */

/*
  override def processDeclarations(processor: PsiScopeProcessor,
      substitutor: PsiSubstitutor,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {

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
*/

}