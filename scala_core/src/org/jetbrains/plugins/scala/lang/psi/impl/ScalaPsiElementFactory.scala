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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElementImpl,ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

import com.intellij.openapi.util.TextRange

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.util.CharTable
import com.intellij.lexer.Lexer
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.impl.source.CharTableImpl

object ScalaPsiElementFactory {

  private val DUMMY = "dummy." 

  def createExpressionFromText(buffer: String, manager: PsiManager): ScExpression = {
    val text = "class a {val b = " + buffer + "}"

    val dummyFile = PsiFileFactory.getInstance(manager.getProject).createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.getTypeDefinitions()(0)
    val p = classDef.members()(0).asInstanceOf[ScPatternDefinition]
    p.expr
  }

  def createDummyParams(manager: PsiManager): ScParameters = {
    val text = "class a {def foo()}"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject()).
            createFileFromText(DUMMY + ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension(), text).asInstanceOf[ScalaFile]
    val classDef = dummyFile.getTypeDefinitions()(0)
    val function = classDef.functions()(0)
    return function.paramClauses
  }
}
