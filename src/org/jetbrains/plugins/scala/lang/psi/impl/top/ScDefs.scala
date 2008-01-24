package org.jetbrains.plugins.scala.lang.psi.impl.top.defs

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem

import org.jetbrains.plugins.scala.lang.psi.containers._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
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
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._

/**********************************************************************************************************************/
/******************************************* Concrete templates *******************************************************/
/**********************************************************************************************************************/

/*
*   Class definition implementation
*/
case class ScClassDefinition(node: ASTNode) extends ScTypeDefinition (node) with LocalContainer{

  override def setName(s: String) = this

  def getExtendsBlock = getChild(ScalaElementTypes.EXTENDS_BLOCK).asInstanceOf[ScExtendsBlock]

  override def getTemplateParents = if (getExtendsBlock != null) {
    getExtendsBlock.getTemplateParents
  } else null

  override def getMainParentName = {
    if (getTemplateParents != null &&
    getTemplateParents.getMainConstructor != null){
      getTemplateParents.getMainConstructor.getClassName
    } else {
      null
    }
  }

  override def getMixinParentsNames = {
    if (getTemplateParents != null){
      val temp = getTemplateParents.getMixinParents
      temp
      getTemplateParents.getMixinParents.toList
    } else {
      Nil: List[ScStableId]
    }
  }


  def paramClauses = childrenOfType[ScParamClause](TokenSet.create(Array(ScalaElementTypes.PARAM_CLAUSE)))

  import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
  def getParameters = ((paramClauses :\ (Nil: List[ScParam]))((y: ScParamClause, x: List[ScParam]) =>
    y.params.toList ::: x))


  import com.intellij.psi.scope._
  import com.intellij.psi._
  override def getVariable(processor: PsiScopeProcessor,
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



  override def toString: String = super .toString + ": " + "class"

  override def getIcon(flags: Int) = Icons.CLASS
}

/*
*   Object definition implementation
*/
case class ScObjectDefinition(node: ASTNode) extends ScTmplDef (node) {

  override def toString: String = super .toString + ": " + "object"
  override def getIcon(flags: Int) = Icons.OBJECT
  override def setName(s: String) = this
}

/*
*   Trait definition implementation
*/
case class ScTraitDefinition(node: ASTNode) extends ScTypeDefinition (node) with LocalContainer {

  def getExtendsBlock = getChild(ScalaElementTypes.EXTENDS_BLOCK).asInstanceOf[ScExtendsBlock]

  def getMixinParents = if (getExtendsBlock != null) {
    getExtendsBlock.getMixinParents
  } else null

  override def getMainParentName = {
    if (getMixinParents != null &&
    getMixinParents.getMainConstructor != null){
      getMixinParents.getMainConstructor.getClassName
    } else {
      null
    }
  }

  override def getMixinParentsNames = {
    if (getMixinParents != null){
      getMixinParents.getMixinParents.toList
    } else {
      Nil: List[ScStableId]
    }
  }

  override def setName(s: String) = this


  override def toString: String = super .toString + ": " + "trait"

  override def getIcon(flags: Int) = Icons.TRAIT
}


