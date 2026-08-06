package org.example1.usage

import org.example1.declaration.data._
import org.example1.declaration.{X => X_Renamed}

trait Usage_WithRename_OfMovedClass_OtherImportWildcard {
  val a: A = ???
  val b: B = ???
  val c: C = ???
  val d: D = ???

  val x: X_Renamed = ???
}