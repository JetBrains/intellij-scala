package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.Template
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScParamClauses
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.ScTemplateStatement
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTopDefTemplate
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.resolve.references._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 20.02.2008
* Time: 18:45:27
* To change this template use File | Settings | File Templates.
*/

class ScClassImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScClass{

  def setName(s: String) = this

  def getExtendsBlock = getChild(ScalaElementTypes.EXTENDS_BLOCK).asInstanceOf[ScExtendsBlock]

  def getTemplateParents = null /*if (getExtendsBlock != null) {
    getExtendsBlock.getTemplateParents
  } else null*/

  def getMainParentName = null /*{
    if (getTemplateParents != null &&
    getTemplateParents.getMainConstructor != null){
      getTemplateParents.getMainConstructor.getClassName
    } else {
      null
    }
  }*/

  /*override def getMixinParentsNames = {
    if (getTemplateParents != null){
      val temp = getTemplateParents.getMixinParents
      temp
      getTemplateParents.getMixinParents.toList
    } else {
      Nil: List[ScStableId]
    }
  }*/


  //def paramClauses = childrenOfType[ScParamClause](TokenSet.create(Array(ScalaElementTypes.PARAM_CLAUSE)))

 /* import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
  def getParameters = ((paramClauses :\ (Nil: List[ScParam]))((y: ScParamClause, x: List[ScParam]) =>
    y.params.toList ::: x))*/


  import com.intellij.psi.scope._
  import com.intellij.psi._
  def getVariable(processor: PsiScopeProcessor,
      substitutor: PsiSubstitutor): Boolean = {
    // Scan for parameters
    /*
        for (val classParamDef <- getParameters; classParamDef.getTextOffset <= varOffset) {
          if (classParamDef != null && ! processor.execute(classParamDef, substitutor)) {
            return false
          }
        }
    */
    return true
  }

  /**
  *  Process declarations of parameters
  */
  /*
    override def processDeclarations(processor: PsiScopeProcessor,
        substitutor: PsiSubstitutor,
        lastParent: PsiElement,
        place: PsiElement): Boolean = {
      import org.jetbrains.plugins.scala.lang.resolve.processors._

      if (processor.isInstanceOf[ScalaLocalVariableResolveProcessor]){
          this.varOffset = processor.asInstanceOf[ScalaLocalVariableResolveProcessor].offset
        getVariable(processor, substitutor)
      } else true
    }
  */



  override def toString: String = "ScClass"

  override def getIcon(flags: Int) = Icons.CLASS
}