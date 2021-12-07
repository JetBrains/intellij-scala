package org.example1.usage

import org.example1.declaration.data.{A => A_Renamed, B => B_Renamed, _}

trait Usage_MergeToExisting_Imports_WIth_Wildcard_WithRename_OfOtherClass {
  val a: A_Renamed = ???
  val b: B_Renamed = ???
  val c: C = ???
  val d: D = ???

  val x: X = ???
}