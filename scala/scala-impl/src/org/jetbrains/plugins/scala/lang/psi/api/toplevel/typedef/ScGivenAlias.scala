package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Examples: {{{
 *     //1. given alias declaration
 *     given String
 *     given myGiven: String
 *
 *     //2. given alias definition
 *     given String = ???
 *     given myGiven: String = ???
 *
 *     //3. given structural instance definition
 *     given MyType with MyTrait with {}
 *     given value: MyType with MyTrait with {}
 * }}}
 */
trait ScGivenAlias extends ScGiven with ScFunction {
  /**
   * Returns `Some` type annotation for given alias declaration and valid/complete given alias definition<br>
   *
   * Returns `None` for given alias definition which is incomplete.<br>
   * This can happen during typing or during completion of alias type annotation<br>
   * Example: `given value: <caret> = ???`<br>
   *
   * Returns `None` for given structural instance definition<br>
   */
  def typeElement: Option[ScTypeElement]

  override def getNavigationElement: PsiElement =
    if (nameElement.isDefined) super.getNavigationElement else typeElement.getOrElse(getFirstChild)
}
