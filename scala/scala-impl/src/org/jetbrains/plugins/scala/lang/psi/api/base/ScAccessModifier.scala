package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

trait ScAccessModifier extends ScalaPsiElement {

  def idText: Option[String]

  def isPrivate: Boolean

  def isProtected: Boolean

  /**
   * @return true for modifiers with `[this]`, for example: {{{
   *   private[this] def foo = ???
   *   protected[this] def bar = ???
   * }}}
   */
  def isThis: Boolean

  def isUnqualifiedPrivateOrThis: Boolean = isPrivate && (getReference == null || isThis)
}