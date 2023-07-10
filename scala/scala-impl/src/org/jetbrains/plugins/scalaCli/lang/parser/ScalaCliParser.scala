package org.jetbrains.plugins.scalaCli.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LightPsiParser
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scalaCli.lang.lexer.ScalaCliTokenTypes._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.tLINE_COMMENT

class ScalaCliParser extends PsiParser with LightPsiParser {

  /**
   * Parses a [[org.jetbrains.plugins.scalaCli.psi.impl.ScCliDirectiveImpl]].
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
          if (expected.contains(tCLI_DIRECTIVE_KEY)) builder.error("Scala CLI key expected: option, dep, jar, etc.")
          else {
            builder.error("Unexpected token")
          }
          error = true
        }
      }

      builder.advanceLexer()
    }

    val rootMarker = builder.mark

    processCurrentToken(builder, tCLI_DIRECTIVE_PREFIX)
    processCurrentToken(builder, tCLI_DIRECTIVE_COMMAND)
    processCurrentToken(builder, tCLI_DIRECTIVE_KEY)

    while (builder.getTokenType != null) processCurrentToken(builder, tCLI_DIRECTIVE_VALUE, tCLI_DIRECTIVE_COMMA, tLINE_COMMENT)

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