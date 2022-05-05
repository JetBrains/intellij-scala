package org.jetbrains.plugins.scala.editor

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFor, ScIf, ScWhile}

// see https://docs.scala-lang.org/scala3/reference/other-new-features/indentation.html
object Scala3IndentationBasedSyntaxUtils {
  def indentedRegionCanStart(leaf: PsiElement): Boolean = leaf.elementType match {
      case ScalaTokenTypes.tASSIGN |
           ScalaTokenTypes.tFUNTYPE |
           ScalaTokenType.ImplicitFunctionArrow |
           ScalaTokenTypes.tCHOOSE |
           ScalaTokenTypes.kYIELD |
           ScalaTokenTypes.kDO |
           ScalaTokenType.ThenKeyword |
           ScalaTokenTypes.kELSE |
           ScalaTokenTypes.kTRY |
           ScalaTokenTypes.kFINALLY |
           ScalaTokenTypes.kCATCH |
           ScalaTokenTypes.kMATCH |
           ScalaTokenTypes.kRETURN |
           ScalaTokenTypes.kTHROW =>
        true
      case ScalaTokenTypes.tRPARENTHESIS => leaf.parent match {
        case Some(innerIf: ScIf) => innerIf.condition.isDefined && innerIf.thenExpression.isEmpty
        case Some(innerWhile: ScWhile) => innerWhile.condition.isDefined && innerWhile.expression.isEmpty
        case Some(innerFor: ScFor) => innerFor.enumerators.isDefined && innerFor.yieldOrDoKeyword.isEmpty && innerFor.body.isEmpty
        case _ => false
      }
      case ScalaTokenTypes.tRBRACE => leaf.parent match {
        case Some(innerFor: ScFor) => innerFor.enumerators.isDefined && innerFor.yieldOrDoKeyword.isEmpty && innerFor.body.isEmpty
        case _ => false
      }
      case _ =>
        false
  }

  def outdentedRegionCanStart(leaf: PsiElement): Boolean = leaf.elementType match {
    case ScalaTokenType.ThenKeyword |
         ScalaTokenTypes.kELSE |
         ScalaTokenTypes.kDO |
         ScalaTokenTypes.kYIELD |
         ScalaTokenTypes.kCATCH |
         ScalaTokenTypes.kFINALLY |
         ScalaTokenTypes.kMATCH => false
    case _ => true
  }

  def continuesCompoundStatement(leaf: PsiElement): Boolean = leaf.elementType match {
    case ScalaTokenTypes.kMATCH => false
    case _ => !outdentedRegionCanStart(leaf)
  }
}
