package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

trait ScAccessModifier extends ScalaPsiElement {

  def idText: Option[String]

  def isPrivate: Boolean
  def isProtected: Boolean
  def isThis: Boolean
  def isUnqualifiedPrivateOrThis: Boolean = isPrivate && (getReference == null || isThis)
}