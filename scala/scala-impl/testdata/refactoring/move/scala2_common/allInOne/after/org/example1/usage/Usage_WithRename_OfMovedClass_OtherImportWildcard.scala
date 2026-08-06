package org.example1.usage

import org.example1.declaration.data.{X => X_Renamed, _}

trait Usage_WithRename_OfMovedClass_OtherImportWildcard {
  val a: A = ???
  val b: B = ???
  val c: C = ???
  val d: D = ???

  val x: X_Renamed = ???
}