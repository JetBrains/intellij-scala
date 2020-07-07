package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

trait ScBraceless extends ScalaPsiElement {
  def isBraceless: Boolean
}
