package org.jetbrains.plugins.scala.lang.typechecker.types

/** 
* @author ilyas
*/

import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem

import org.jetbrains.plugins.scala.lang.psi.containers._
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


case class FunctionType(funParams: List[AbstractType],
    result: AbstractType,
    genericParams: List[ScTypeDefinition]) extends AbstractType(genericParams){

  def reduce = result

  def canBeAppliedTo(argTypes: List[AbstractType]): Boolean = {
    if (argTypes == null || funParams == null) return false;
    if (argTypes.length != funParams.length) return false;
    for (val argTypePair <- argTypes.zip[AbstractType](funParams)) {
      if (argTypePair._1 == null || argTypePair._2 == null) return false;
      if (! argTypePair._1.conformsTo(argTypePair._2)) {
        return false;
      }
    }
    return true;
  }

  def conformsTo(otherType: AbstractType): Boolean = otherType match {
    case FunctionType(otherParams, otherResult, _) if otherParams != null && otherResult != null => {
      // todo add subtyping
      if (otherParams.length != funParams.length) return false;
      for (val pair <- otherParams.zip[AbstractType](funParams)) {
        // contravariant subtyping
        if (! pair._2.conformsTo(pair._1)) {
          return false;
        }
      }
      return otherResult.conformsTo(result)
    }
    case _ => false
  }


  def getRepresentation = {
    var res = ""
    for (val param <- funParams) {
      res = res + param.getRepresentation + ","
    }
    if (res.length > 0) {
      "(" + res.substring(0, res.length - 1) + ")=>" + result.getRepresentation
    } else {
      "()=>" + result.getRepresentation
    }
  }

}