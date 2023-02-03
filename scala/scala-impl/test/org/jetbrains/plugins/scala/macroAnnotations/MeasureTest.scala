package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase

class MeasureTest extends ScalaFixtureTestCase {

  def testTracing(): Unit = {
    class Foo {
      @Measure
      def currentTime(): Long = System.currentTimeMillis()
    }

    checkTracer("Foo.currentTime", totalCount = 4, actualCount = 4) {
      val foo = new Foo
      foo.currentTime()
      foo.currentTime()

      new Foo().currentTime()
      new Foo().currentTime()
    }
  }

}
