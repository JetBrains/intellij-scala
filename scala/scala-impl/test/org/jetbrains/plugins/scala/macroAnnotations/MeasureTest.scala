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

  def testTracingExpr(): Unit = {
    class Foo(name: String) {

      @Measure(name)
      def currentTime(): Long = System.currentTimeMillis()
    }

    checkTracer("Foo.currentTime name == bar", totalCount = 2, actualCount = 2) {
      val foo = new Foo("bar")
      foo.currentTime()
      foo.currentTime()

      new Foo("lol").currentTime()
      new Foo("lol").currentTime()
    }

  }

  def testTracingExprs(): Unit = {
    class Foo(name: String) {

      @Measure(name, arg)
      def bar(arg: Int): Long = System.currentTimeMillis()
    }

    checkTracer("Foo.bar name == bar, arg == 1", totalCount = 1, actualCount = 1) {
      val foo = new Foo("bar")
      foo.bar(1)
      foo.bar(2)
    }

  }

}
