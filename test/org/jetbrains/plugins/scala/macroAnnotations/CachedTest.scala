package org.jetbrains.plugins.scala.macroAnnotations

import org.junit.Assert


/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/17/15.
 */
class CachedTest extends CachedTestBase {
  def testNoParametersSingleThread(): Unit = {
    val foo = new Foo
    val firstRes: Long = foo.currentTime()
    Thread.sleep(1)
    Assert.assertEquals(firstRes, foo.currentTime())
    Thread.sleep(1)
    val oldModCount = foo.getManager.getModificationTracker.getModificationCount
    foo.getManager.getModificationTracker.incCounter()
    val newModCount = foo.getManager.getModificationTracker.getModificationCount
    Assert.assertTrue(oldModCount < newModCount)
    val secondRes: Long = foo.currentTime()
    Assert.assertTrue(firstRes < secondRes)
    Thread.sleep(1)
    Assert.assertEquals(secondRes, foo.currentTime())
  }

  class Foo extends Managed {
    @Cached
    def currentTime(): Long = System.currentTimeMillis()
  }
}
