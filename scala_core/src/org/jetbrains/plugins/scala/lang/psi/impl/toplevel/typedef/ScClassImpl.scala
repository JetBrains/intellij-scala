package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

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

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

/** 
* @autor Alexander.Podkhalyuzin
*/

class ScClassImpl(node: ASTNode) extends ScTypeDefinitionImpl(node) with ScClass{

  def getTemplateParents = null

  def getMainParentName = null

  override def toString: String = "ScClass"

  override def getIconInner = Icons.CLASS

  //todo implement me!
  def parameters = Seq.empty

  import com.intellij.psi.{scope, PsiElement, ResolveState}
  import scope.PsiScopeProcessor 
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    constructor match {
      case Some(c) => for (p <- c.parameters.params) {if (!processor.execute(p, state)) return false}
      case None => ()
    }
    return super.processDeclarations(processor, state, lastParent, place)
  }
}