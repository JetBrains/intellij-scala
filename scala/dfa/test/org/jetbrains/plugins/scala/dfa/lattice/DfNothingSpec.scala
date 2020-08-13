package org.jetbrains.plugins.scala.dfa
package lattice

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec

class DfNothingSpec extends AnyPropSpec with Matchers {
  property("DfNothing is not concrete") {
    DfNothing should not be a [DfAny.Concrete]
  }
}
