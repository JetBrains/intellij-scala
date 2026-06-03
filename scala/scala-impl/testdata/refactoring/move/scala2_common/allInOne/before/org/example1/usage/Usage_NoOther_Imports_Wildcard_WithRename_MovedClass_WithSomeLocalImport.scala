package org.example1.usage

import org.example1.declaration.{X => X_Renamed, _}

trait Usage_NoOther_Imports_Wildcard_WithRename_MovedClass_WithSomeLocalImport {

  val x1: X_Renamed = ???
  val y: Y = ???
  val z: Z = ???

  def myScope(): Unit = {
    import org.example1.declaration.X
    val x2: X = ???
  }
}