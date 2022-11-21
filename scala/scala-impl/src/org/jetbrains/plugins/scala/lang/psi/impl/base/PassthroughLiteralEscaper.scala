package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

import java.lang

// todo: remove this class cause it's unused
class PassthroughLiteralEscaper(val literal: ScStringLiteral) extends LiteralTextEscaper[ScStringLiteral](literal) {
  override def decode(rangeInsideHost: TextRange, outChars: lang.StringBuilder): Boolean = {
    TextRange.assertProperRange(rangeInsideHost)
    outChars.append(myHost.getText, rangeInsideHost.getStartOffset, rangeInsideHost.getEndOffset)
    true
  }

  override def getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int = {
    var offset = offsetInDecoded + rangeInsideHost.getStartOffset
    if (offset < rangeInsideHost.getStartOffset) offset = rangeInsideHost.getStartOffset
    if (offset > rangeInsideHost.getEndOffset) offset = rangeInsideHost.getEndOffset
    offset
  }

  /** ATTENTION: <br>
   * This is a a hacky workaround for SCL-11710<br>
   * For now this method is only used in one place:
   * [[com.intellij.psi.impl.source.tree.injected.InjectionRegistrarImpl#createShred]] <br>
   * It is used very indirectly to determine which handler to use on EnterAction: host file editor or injected file editor.
   * Host handler is only used if isOneLine=true, otherwise injected file handler is used.
   * For now we would like Enter action to be handled by [[org.jetbrains.plugins.scala.editor.enterHandler.MultilineStringEnterHandler]]
   *
   * @see [[com.intellij.openapi.editor.actionSystem.EditorActionHandler#doIfEnabled]]
   * @see [[com.intellij.codeInsight.editorActions.EnterHandler#isEnabledForCaret]]
   */
  override def isOneLine: Boolean = {
    // TODO: fix platform in order to do enter handling for host file more directly
    //myHost.getValue match {
    //  case str: String => str.indexOf('\n') < 0
    //  case _ => false
    // }
    true
  }
}