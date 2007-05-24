package org.jetbrains.plugins.scala.lang.typechecker

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
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs._
import org.jetbrains.plugins.scala.lang.typechecker.types._

class ScalaTypeChecker extends IScalaTypeChecker{

  def getTypeByTerm(term: PsiElement): AbstractType = {
    if (term.getReference != null) {
      return term.getReference.resolve match {
        case refDef: ScReferenceId => {
          refDef.getType
        }
        case _ => null
      }
    }
    term match {
      case par: ScParenthesisedExpr => par.getAbstractType
      case af: ScAnFunImpl =>af.getAbstractType
      case newTmpl : ScNewTemplateDefinition => {
        newTmpl.getAbstractType
      }
      case _ => null
    }
  }

  def getTypeRepresentation(term: PsiElement): String = getTypeByTerm(term) match {
    case a: AbstractType => a.getRepresentation
    case _ => "Couldn't infer type of given term!"
  }



}