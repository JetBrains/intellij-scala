package org.example1.usage

import org.example1.declaration.data.{A, B, C}
import org.example1.declaration.{X => X_Renamed}

trait Usage_WithRename_OfMovedClass_OtherImportCommon3 {
  val a: A = ???
  val b: B = ???
  val c: C = ???

  val x: X_Renamed = ???
}