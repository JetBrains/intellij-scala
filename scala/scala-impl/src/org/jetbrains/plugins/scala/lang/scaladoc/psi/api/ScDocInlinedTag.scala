package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * example: {{{
 *  /**
 *   * {@link org.example.com}
 *   */
 *   class MyClass
 * }}}
 */
trait ScDocInlinedTag extends ScalaPsiElement {
  def name: String
  def nameElement: ScPsiDocToken
  def valueText: Option[String]
}
