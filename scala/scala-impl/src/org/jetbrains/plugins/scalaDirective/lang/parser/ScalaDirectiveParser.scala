package org.jetbrains.plugins.scalaDirective.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LightPsiParser
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveTokenTypes._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.tLINE_COMMENT

class ScalaDirectiveParser extends PsiParser with LightPsiParser {

  /**
   * Parses a [[org.jetbrains.plugins.scalaDirective.psi.impl.ScDirectiveImpl]].
   *
   * The approach is to stop processing tokens after an unexpected or erroneous token is encountered.
   * This is tracked via <code>noErrorsOccurred</code>.
   */
  override def parseLight(root: IElementType, builder: PsiBuilder): Unit = {

    var error = false

    /**
     * If the current token is one of the `expected` types, just advance the lexer.
     * If it isn't, insert an error element into the tree, set [[error]] to true and advance the lexer.
     */
    def processCurrentToken(builder: PsiBuilder, expected: IElementType*): Unit = {
      if (!error) {
        val currentTokenType = builder.getTokenType
        if (!expected.contains(currentTokenType)) {
          if (expected.contains(tDIRECTIVE_KEY)) builder.error("Scala directive key expected: option, dep, jar, etc.")
          else {
            builder.error("Unexpected token")
          }
          error = true
        }
      }

      builder.advanceLexer()
    }

    val rootMarker = builder.mark

    processCurrentToken(builder, tDIRECTIVE_PREFIX)
    processCurrentToken(builder, tDIRECTIVE_COMMAND)
    processCurrentToken(builder, tDIRECTIVE_KEY)

    while (builder.getTokenType != null) processCurrentToken(builder, tDIRECTIVE_VALUE, tDIRECTIVE_COMMA, tLINE_COMMENT)

    rootMarker.done(root)
  }

  /**
   * @see [[parseLight]]
   */
  override def parse(@NotNull root: IElementType, @NotNull builder: PsiBuilder): ASTNode = {
    parseLight(root, builder)
    builder.getTreeBuilt
  }
}