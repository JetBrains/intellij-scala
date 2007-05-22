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


case class ValueType(ownType: ScTypeDefinition, genericParams: List[ScTypeDefinition])
extends AbstractType(genericParams){

  def reduce = this

  def getBaseTypes =  ownType.getAllParents

  def conformsTo(otherType: AbstractType) = otherType match {
    case ValueType(otherType, _) if otherType != null => {
      // todo add subtyping
      otherType.equals(ownType)
    }
    case _ => false
  }

  def getRepresentation = {
    var res = ownType.getName
    if (getBaseTypes.length > 0) {
      res = res + " <: "
      for (val baseType <- getBaseTypes) {
        if (baseType.isInstanceOf[ScTypeDefinition]) {
          res = res + baseType.getQualifiedName + ","
        }
      }
      res = res.substring(0, res.length - 1)
    }
    res
  }

}