package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.lang.{ASTNode, Language}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{ICompositeElementType, IErrorCounterReparseableElementType}
import com.intellij.psi.util.PsiUtilCore

import scala.annotation.tailrec

abstract class ScCodeBlockElementType extends IErrorCounterReparseableElementType(
  "block of expressions",
  ScalaLanguage.INSTANCE
) with ICompositeElementType {

  import IErrorCounterReparseableElementType._
  import lexer.ScalaTokenTypes.{tLBRACE => LeftBrace, tRBRACE => RightBrace}

  override final def createCompositeNode: ASTNode = createNode(null)

  override final def getErrorsCount(buf: CharSequence,
                                    fileLanguage: Language,
                                    project: Project): Int = {
    val scalaLexer = new lexer.ScalaLexer(false, null)
    scalaLexer.start(buf)
    scalaLexer.getTokenType match {
      case LeftBrace => iterate(1)(scalaLexer)
      case _ => FATAL_ERROR
    }
  }

  @tailrec
  private def iterate(balance: Int)
                     (implicit scalaLexer: lexer.ScalaLexer): Int = {
    scalaLexer.advance()
    scalaLexer.getTokenType match {
      case null => balance
      case _ if balance == NO_ERRORS => FATAL_ERROR
      case LeftBrace => iterate(balance + 1)
      case RightBrace => iterate(balance - 1)
      case _ => iterate(balance)
    }
  }
}

object ScCodeBlockElementType {

  object BlockExpression extends ScCodeBlockElementType with SelfPsiCreator {

    override def createNode(text: CharSequence): ASTNode = new psi.impl.expr.ScBlockExprImpl(this, text)

    override def createElement(node: ASTNode): PsiElement = PsiUtilCore.NULL_PSI_ELEMENT

    override def getLanguageForParser(psi: PsiElement): Language =
      Option(psi).map(_.getLanguage) match {
        // including WorksheetLanguage3
        case Some(lang) if lang.isKindOf(Scala3Language.INSTANCE) => Scala3Language.INSTANCE
        case _                                                    => super.getLanguageForParser(psi)
      }
  }

}