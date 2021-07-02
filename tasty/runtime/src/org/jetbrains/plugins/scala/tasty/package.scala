package org.jetbrains.plugins.scala

package object tasty {
  type Name = TermName

  type SimpleName = TermName

  val EmptyTermName: TermName = new TermName("<empty>")
}
