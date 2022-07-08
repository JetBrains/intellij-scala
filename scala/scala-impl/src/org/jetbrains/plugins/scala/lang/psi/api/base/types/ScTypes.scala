package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

trait ScTypes extends ScalaPsiElement {
  def types: Seq[ScTypeElement] = findChildren[ScTypeElement]
}