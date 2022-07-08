package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

trait ScPatterns extends ScalaPsiElement {
  def patterns: Seq[ScPattern]
}