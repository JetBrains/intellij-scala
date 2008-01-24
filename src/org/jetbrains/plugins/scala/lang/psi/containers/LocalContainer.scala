package org.jetbrains.plugins.scala.lang.psi.containers

/** 
* Created by IntelliJ IDEA.
* User: Ilya.Sergey
* Date: 07.04.2007
* Time: 16:28:26
* To change this template use File | Settings | File Templates.
*/

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.psi.search._

import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.resolve.processors._
import org.jetbrains.plugins.scala.lang.psi.impl.top._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.psi.javaView._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.ScalaValue
import com.intellij.psi.tree._

/**
*  Various blocks
*/
trait LocalContainer extends ScalaPsiElement {

  var varOffset = 0
  val varSet = TokenSet.create(Array(ScalaElementTypes.VARIABLE_DEFINITION,
          ScalaElementTypes.VARIABLE_DECLARATION))

  val funSet = TokenSet.create(Array(ScalaElementTypes.FUNCTION_DEFINITION,
          ScalaElementTypes.FUNCTION_DECLARATION))

  val valSet = TokenSet.create(Array(ScalaElementTypes.PATTERN_DEFINITION,
          ScalaElementTypes.VALUE_DECLARATION))

  /**
  *   Returns all local variables in current block
  *
  */
  def getVariables = childrenOfType[ScalaVariable](varSet).toList

  /**
  *   Returns all local variables in current block
  *
  */
  def getValues = childrenOfType[ScalaValue](valSet).toList


  /**
  *  Scans for variable definitions in current block
  */
  def getVariable(processor: PsiScopeProcessor,
          substitutor: PsiSubstitutor): Boolean = {

    // Scan for variable
/*
    for (val varDef <- getVariables ::: getValues ::: childrenOfType[ScFunction](funSet).toList;
        varDef.getTextOffset <= varOffset) {
      if (varDef != null && ! processor.execute(varDef, substitutor)) {
        return false
      }
    }
*/
    return true
  }


}