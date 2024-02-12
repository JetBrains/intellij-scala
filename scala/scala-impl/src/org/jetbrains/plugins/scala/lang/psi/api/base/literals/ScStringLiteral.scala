package org.jetbrains.plugins.scala.lang.psi.api.base.literals

import com.intellij.psi.{ContributedReferenceHost, PsiLanguageInjectionHost}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

trait ScStringLiteral extends ScLiteral
  with PsiLanguageInjectionHost
  with ContributedReferenceHost {

  override protected type V = String

  override def isSimpleLiteral: Boolean = true

  /**
   * @return true - if the string literal has closing quotes<br>
   *         false - if the string is unclosed (like `"test` or `"""test` or `s"""test""`)<br>
   *         NOTE: an incomplete string literal without closing quites is still parsed as `ScStringLiteral` instance but with extra error element
   */
  def hasValidClosingQuotes: Boolean

  /**
   * @return true - if the string uses triple quotes.<br>
   *         Note: multiline string can actually have a single content line, but still use tripple quotes.<br>
   *         Examples of multiline strings: {{{
   *            //plain
   *            """text"""
   *
   *            //interpolated
   *            s"""text"""
   *
   *            //with margin
   *            """line 1
   *              |line2"""
   *
   *            //without margin
   *            s"""line 1
   *                  line2"""
   *         }}}
   *         false - otherwise
   */
  def isMultiLineString: Boolean
}

object ScStringLiteral {
  def unapply(lit: ScStringLiteral): Option[String] = Option(lit.getValue)
}