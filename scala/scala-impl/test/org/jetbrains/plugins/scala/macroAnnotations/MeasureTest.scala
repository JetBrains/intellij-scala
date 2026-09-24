package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.caches.measure
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class MeasureTest extends ScalaFixtureTestCase {

  def testTracing(): Unit = {
    class Foo {
      def currentTime(): Long = measure("currentTime") {
        System.currentTimeMillis()
      }
    }

    checkTracer(lambdaRegex("MeasureTest$Foo$1", "currentTime"), totalCount = 4, actualCount = 4) {
      val foo = new Foo
      foo.currentTime()
      foo.currentTime()

      new Foo().currentTime()
      new Foo().currentTime()
    }
  }

}
