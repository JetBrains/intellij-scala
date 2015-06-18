package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._
import scala.meta.Semantics

class TreeConverterDenotationsTest extends TreeConverterTestBaseWithLibrary {

  def testSimple() {
    val a = convert(
      "var a: scala.collection.immutable.List[Int] = _"
    )
    val b = a.show[Semantics]
    ""
  }

}
