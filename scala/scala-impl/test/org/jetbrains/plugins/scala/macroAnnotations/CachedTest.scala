package org.jetbrains.plugins.scala.macroAnnotations

import org.junit.Assert


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
    Assert.assertEquals(firstRes, foo.currentTime())
    Thread.sleep(1)
    val tracker = foo.getModTracker
    val oldModCount = tracker.getModificationCount
    tracker.incCounter()
    val newModCount = tracker.getModificationCount
    Assert.assertTrue(oldModCount < newModCount)
    val secondRes: Long = foo.currentTime()
    Assert.assertTrue(firstRes < secondRes)
    Thread.sleep(1)
    Assert.assertEquals(secondRes, foo.currentTime())
  }

  def testModificationTrackers(): Unit = {
    object Foo extends Managed {
      @Cached(ModCount.getModificationCount, this)
      def currentTime: Long = System.currentTimeMillis()
    }

    val firstRes = Foo.currentTime
    Thread.sleep(1)
    Assert.assertEquals(firstRes, Foo.currentTime)
    Foo.getModTracker.incCounter()
    Assert.assertTrue(firstRes < Foo.currentTime)
  }

  def testWithParameters(): Unit = {
    object Foo extends Managed {
      @Cached(ModCount.getModificationCount, this)
      def currentTime(a: Int, b: Int): Long = System.currentTimeMillis()
    }

    val firstRes = Foo.currentTime(0, 0)
    Thread.sleep(1)
    Assert.assertEquals(firstRes, Foo.currentTime(0, 0))
    Assert.assertTrue(firstRes < Foo.currentTime(1, 0))
    Assert.assertTrue(firstRes < Foo.currentTime(0, 1))
    Foo.getModTracker.incCounter()
    val secondRes = Foo.currentTime(0, 0)
    Assert.assertTrue(firstRes < secondRes)
    Thread.sleep(1)
    Assert.assertEquals(secondRes, Foo.currentTime(0, 0))
  }
}
