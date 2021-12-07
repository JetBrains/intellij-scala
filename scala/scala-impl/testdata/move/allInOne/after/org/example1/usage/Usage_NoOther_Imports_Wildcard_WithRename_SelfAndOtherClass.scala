package org.example1.usage

import org.example1.declaration.data.{X => X_Renamed}
import org.example1.declaration.{Y => Y_Renamed, _}

trait Usage_NoOther_Imports_Wildcard_WithRename_SelfAndOtherClass {
  val x: X_Renamed = ???
  val y: Y_Renamed = ???
  val z: Z = ???
}