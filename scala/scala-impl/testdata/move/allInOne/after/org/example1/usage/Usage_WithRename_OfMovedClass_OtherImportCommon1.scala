package org.example1.usage

import org.example1.declaration.data.{A, X => X_Renamed}

trait Usage_WithRename_OfMovedClass_OtherImportCommon1 {
  val a: A = ???

  val x: X_Renamed = ???
}