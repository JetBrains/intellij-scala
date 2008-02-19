package org.jetbrains.plugins.scala.lang.psi.impl

import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType

import com.intellij.psi.PsiFile
import com.intellij.lang.ParserDefinition

import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElementImpl,ScalaFile}

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.psi._
import com.intellij.psi.impl.source.CharTableImpl

object ScalaPsiElementFactory {

  private val DUMMY = "dummy." 
/*
  def createExpressionFromText(buffer: String, manager: PsiManager): ASTNode = {
    def isExpr = (elementType: IElementType) => (ScalaElementTypes.EXPRESSION_BIT_SET.contains(elementType))

    val definition: ParserDefinition = ScalaFileType.SCALA_FILE_TYPE.getLanguage.getParserDefinition
    //    if (definition != null) ...

    val facade = JavaPsiFacade.getInstance(manager.getProject)
    val text = "class a {" + buffer + "}"

    val dummyFile: PsiFile = facade.getElementFactory().createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text)

    val classDef = dummyFile.getFirstChild
    val topDefTmpl = classDef.getLastChild
    val templateBody = topDefTmpl.getFirstChild.asInstanceOf[ScalaPsiElementImpl]

    val expression = templateBody.childSatisfyPredicateForElementType(isExpr)

    if (expression == null) return null

    expression.asInstanceOf[ScalaExpression].getNode
  }
*/

/*
  def createIdentifierFromText(id: String, manager: PsiManager): ASTNode = {
    val definition: ParserDefinition = ScalaFileType.SCALA_FILE_TYPE.getLanguage.getParserDefinition
    val text = "class " + id + "{}"

    val dummyFile: ScalaFile = manager.getElementFactory().createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]

    val classDef = dummyFile.getTmplDefs.head
    classDef.nameNode.getNode
  }
*/

/*
  def createTemplateStatementFromText(buffer: String, manager: PsiManager): ASTNode = {
    def isStmt = (element: PsiElement) => (element.isInstanceOf[ScTemplateStatement])

    val pareserDefinition: ParserDefinition = ScalaFileType.SCALA_FILE_TYPE.getLanguage.getParserDefinition

    val text = "class a {" + buffer + "}"

    val dummyFile: PsiFile = manager.getElementFactory().createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text)

    val classDef = dummyFile.getFirstChild
    val topDefTmpl = classDef.getLastChild
    val templateBody = topDefTmpl.getFirstChild.asInstanceOf[ScalaPsiElementImpl]

    val stmt = templateBody.childSatisfyPredicateForPsiElement(isStmt)

    if (stmt == null) return null

    stmt.asInstanceOf[ScTemplateStatement].getNode

  }
*/

/*
  def createPattern2FromText(buffer: String, manager: PsiManager): ASTNode = {
    def isPattern2 = (elementType: IElementType) => (ScalaElementTypes.PATTERN2_BIT_SET.contains(elementType))
    def isTemplateStmt = (elementType: IElementType) => (ScalaElementTypes.TMPL_STMT_BIT_SET.contains(elementType))

    val definition: ParserDefinition = ScalaFileType.SCALA_FILE_TYPE.getLanguage.getParserDefinition
    //    if (definition != null) ...
    val text = "class a {" +
    "val " + buffer +
    " = b" +
    "}"

    val dummyFile: PsiFile = manager.getElementFactory().createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text)

    val classDef = dummyFile.getFirstChild
    val topDefTmpl = classDef.getLastChild
    val templateBody = topDefTmpl.getFirstChild.asInstanceOf[ScalaPsiElement]

    val patDef: ScalaPsiElement = templateBody.childSatisfyPredicateForElementType(isTemplateStmt).asInstanceOf[ScalaPsiElement]
    val pattern2 = patDef.childSatisfyPredicateForElementType(isPattern2)

    if (pattern2 == null) return null

    pattern2.asInstanceOf[ScPattern2].getNode
  }
*/
}
