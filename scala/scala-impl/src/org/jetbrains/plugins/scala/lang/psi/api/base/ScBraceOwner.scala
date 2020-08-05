package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

trait ScBraceOwner extends ScalaPsiElement {
  def isEnclosedByBraces: Boolean
}
