package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

trait ScNewTemplateDefinition extends ScExpression with ScTemplateDefinition {
  def firstConstructorInvocation: Option[ScConstructorInvocation]

  //It's very rare case, when we need to desugar apply first.
  def desugaredApply: Option[ScExpression]

  //TODO: rename Anonimous -> Anonymous
  /**
   * @return `true` if new expression creates an instance a new anonymous class<br>
   *         Examples: {{{
   *             new Foo() {}
   *             new Foo() { def foo(): Unit = () }
   * }}}
   *         `false` if new expression creates an instance of existing class<br>
   *         Examples:  {{{
   *             new Foo
   *             new Foo()
   *             new Foo(42)
   * }}}
   */
  def isAnonimous: Boolean
}