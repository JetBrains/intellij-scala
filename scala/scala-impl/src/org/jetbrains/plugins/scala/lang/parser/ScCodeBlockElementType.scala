package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.{ASTNode, Language, LanguageASTFactory}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.{ICompositeElementType, IErrorCounterReparseableElementType}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaLexer, ScalaTokenTypes}

import scala.annotation.tailrec

abstract class ScCodeBlockElementType extends IErrorCounterReparseableElementType(
  "block of expressions",
  ScalaLanguage.INSTANCE
) with ICompositeElementType {

  import IErrorCounterReparseableElementType._

  override final def createCompositeNode: ASTNode = createNode(null)

  // The node (which is also the PSI element) is created by the language AST factory (ScalaASTFactory.createLazy),
  // so that the element type doesn't depend on the PSI implementation.
  // Calls the factory directly instead of ASTFactory.lazy, which would call createNode again.
  override def createNode(text: CharSequence): ASTNode =
    LanguageASTFactory.INSTANCE.forLanguage(getLanguage).createLazy(this, text)

  override final def getErrorsCount(buf: CharSequence,
                                    fileLanguage: Language,
                                    project: Project): Int = {
    val scalaLexer = new ScalaLexer(false)
    scalaLexer.start(buf)
    scalaLexer.getTokenType match {
      case ScalaTokenTypes.tLBRACE => iterate(1)(using scalaLexer)
      case _ => FATAL_ERROR
    }
  }

  @tailrec
  private def iterate(balance: Int)
                     (implicit scalaLexer: ScalaLexer): Int = {
    scalaLexer.advance()
    scalaLexer.getTokenType match {
      case null => balance
      case _ if balance == NO_ERRORS => FATAL_ERROR
      case ScalaTokenTypes.tLBRACE => iterate(balance + 1)
      case ScalaTokenTypes.tRBRACE => iterate(balance - 1)
      case _ => iterate(balance)
    }
  }
}

object ScCodeBlockElementType {

  object BlockExpression extends ScCodeBlockElementType {

    override def getLanguageForParser(psi: PsiElement): Language =
      Option(psi).map(_.getLanguage) match {
        // including WorksheetLanguage3
        case Some(lang) if lang.isKindOf(Scala3Language.INSTANCE) => Scala3Language.INSTANCE
        case _                                                    => super.getLanguageForParser(psi)
      }
  }

}
