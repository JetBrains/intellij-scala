package org.jetbrains.plugins.scala.editor

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType.ThenKeyword
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

object Scala3IndentationBasedSyntaxUtils {
  def isFollowedByOutdent(leaf: PsiElement): Boolean = {
    val leafToken = leaf.lastLeaf.elementType
    leafToken match {
      case ThenKeyword => false
      case ScalaTokenTypes.kELSE => false
      case ScalaTokenTypes.kDO => false
      case ScalaTokenTypes.kYIELD => false
      case ScalaTokenTypes.kCATCH => false
      case ScalaTokenTypes.kFINALLY => false
      case ScalaTokenTypes.kMATCH => false
      case _ => true
    }
  }
}
