package org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs

/** 
*  @author ilyas
*/
import _root_.scala.tools.nsc.util._

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.diagnostic.Logger

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
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.typechecker.types._


case class ScNewTemplateDefinition(node: ASTNode) extends ScTypeDefinition(node) with IfElseIndent with IScalaExpression {
  private var LOG = Logger.getInstance("org.jetbrains.plugins.scala.lang.psi.impl.top.defs.ScNewTemplateDefinition");

  override def toString: String = "New template definition"

  override def nameNode = if (getTemplateParents != null &&
  getTemplateParents.getMainConstructor != null &&
  getTemplateParents.getMainConstructor.getClassName != null) {
    def isName = (elementType: IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
    getTemplateParents.getMainConstructor.getClassName.childSatisfyPredicateForElementType(isName)
  } else {
    this
  }
  
  override def getOwnTemplateStatements = Nil : List[ScTemplateStatement]

  override def getName = "AnonymousClassInstance"

  override def setName(name: String) = this

  override def getTextOffset = super.getTextOffset

  override def isTypeDef: boolean = true

  def getAbstractType = new ValueType(this, null)
}