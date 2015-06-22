package org.jetbrains.plugins.scala.meta

import scala.meta.internal.ast._
import scala.meta._

class TreeConverterDenotationsTest extends TreeConverterTestBaseWithLibrary {

  def testSimple() {
    val a = convert(
      "scala.collection.immutable.List()"
    )
    val b = a.show[Semantics]
    ""
  }

}
