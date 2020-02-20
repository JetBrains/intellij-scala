package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.caches.stats.Tracer
import org.junit.Assert._

import scala.collection.JavaConverters._


/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/17/15.
 */
class CachedTest extends CachedTestBase {
  def testNoParametersSingleThread(): Unit = {
    class Foo extends Managed {
      @Cached(ModCount.getModificationCount, this)
      def currentTime(): Long = System.currentTimeMillis()
    }

    val foo = new Foo
    val firstRes: Long = foo.currentTime()
    Thread.sleep(1)
    assertEquals(firstRes, foo.currentTime())
    Thread.sleep(1)
    val tracker = foo.getModTracker
    val oldModCount = tracker.getModificationCount
    tracker.incCounter()
    val newModCount = tracker.getModificationCount
    assertTrue(oldModCount < newModCount)
    val secondRes: Long = foo.currentTime()
    assertTrue(firstRes < secondRes)
    Thread.sleep(1)
    assertEquals(secondRes, foo.currentTime())
  }

  def testModificationTrackers(): Unit = {
    object Foo extends Managed {
      @Cached(ModCount.getModificationCount, this)
      def currentTime: Long = System.currentTimeMillis()
    }

    val firstRes = Foo.currentTime
    Thread.sleep(1)
    assertEquals(firstRes, Foo.currentTime)
    Foo.getModTracker.incCounter()
    assertTrue(firstRes < Foo.currentTime)
  }

  def testWithParameters(): Unit = {
    object Foo extends Managed {
      @Cached(ModCount.getModificationCount, this)
      def currentTime(a: Int, b: Int): Long = System.currentTimeMillis()
    }

    val firstRes = Foo.currentTime(0, 0)
    Thread.sleep(1)
    assertEquals(firstRes, Foo.currentTime(0, 0))
    assertTrue(firstRes < Foo.currentTime(1, 0))
    assertTrue(firstRes < Foo.currentTime(0, 1))
    Foo.getModTracker.incCounter()
    val secondRes = Foo.currentTime(0, 0)
    assertTrue(firstRes < secondRes)
    Thread.sleep(1)
    assertEquals(secondRes, Foo.currentTime(0, 0))
  }

  def testTracer(): Unit = {
    object Foo extends Managed {
      @Cached(ModCount.getModificationCount, this)
      def currentTime: Long = System.currentTimeMillis()
    }

    Tracer.clearAll()

    checkTracer("Foo.currentTime", totalCount = 3, actualCount = 2) {
      Foo.currentTime
      Foo.currentTime
      Foo.getModTracker.incCounter()
      Foo.currentTime
    }
  }

  def testTracerWithExpr(): Unit = {
    class Foo extends Managed {
      var myVar = 0
      @Cached(ModCount.getModificationCount, this, myVar)
      def bar: Int = myVar
    }

    Tracer.clearAll()

    checkTracer("Foo.bar myVar == 0", totalCount = 2, actualCount = 1) {
      val foo = new Foo
      foo.bar
      foo.bar
      foo.myVar = 1
      foo.bar
    }

    checkTracer("Foo.bar myVar == 1", totalCount = 1, actualCount = 0) {
      val foo = new Foo
      foo.bar
      foo.bar
      foo.myVar = 1
      foo.bar
    }

  }

}
