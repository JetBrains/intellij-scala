package org.jetbrains.plugins.scala.macroAnnotations

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.locks.ReentrantLock

import org.junit.Assert


/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/17/15.
 */
class CachedTest extends CachedTestBase {
  def testNoParametersSingleThread(): Unit = {
    class Foo extends Managed {
      @Cached(synchronized = false, ModCount.getModificationCount, this)
      def currentTime(): Long = System.currentTimeMillis()
    }

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

  def testNoParametersSynchronized(): Unit = {
    object Foo extends Managed {
      @volatile
      var cachedFunctionHasBeenRan: Boolean = false

      @volatile
      var assertsFailed = 0

      val allThreadsStartedLock: ReentrantLock = new ReentrantLock()

      @Cached(synchronized = true, ModCount.getModificationCount, this)
      def runSynchronized(): Unit = {

        allThreadsStartedLock.lock()
        try {
          Assert.assertTrue(!cachedFunctionHasBeenRan)
          cachedFunctionHasBeenRan = true
        } finally {
          allThreadsStartedLock.unlock()
        }
      }
    }

    def setUpThreads(): (Thread, Thread) = {
      Foo.allThreadsStartedLock.lock()
      val thread1 = new Thread(new Runnable {
        override def run(): Unit = Foo.runSynchronized()
      })
      val thread2 = new Thread(new Runnable {
        override def run(): Unit = Foo.runSynchronized()
      })

      val eh = new UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable): Unit = e match {
          case _: AssertionError => Foo.assertsFailed += 1
          case _ =>
        }
      }
      thread1.setUncaughtExceptionHandler(eh)
      thread2.setUncaughtExceptionHandler(eh)

      thread1.start() //this thread should be waiting on acquiring the lock
      thread2.start() //this thread should be waiting outside synchronized block
      //NOTE: we are not guaranteed which thread will be waiting where. They might switch, but that's still fine

      while (thread1.getState != Thread.State.WAITING && thread2.getState != Thread.State.WAITING) {
        //busy waiting is bad, but this is in a test, so it is fine. Should put a timeout here?
        Thread.`yield`()
      }
      Foo.allThreadsStartedLock.unlock()
      (thread1, thread2)
    }

    val (t1, t2) = setUpThreads()
    t1.join()
    t2.join()
    Assert.assertEquals(0, Foo.assertsFailed)
  }

  def testModificationTrackers(): Unit = {
    object Foo extends Managed {
      @Cached(synchronized = false, modificationCount = ModCount.getModificationCount, this)
      def currentTime: Long = System.currentTimeMillis()

      def getProject = myFixture.getProject
    }

    val firstRes = Foo.currentTime
    Thread.sleep(1)
    Assert.assertEquals(firstRes, Foo.currentTime)
    Foo.getManager.getModificationTracker.incOutOfCodeBlockModificationCounter()
    Assert.assertTrue(firstRes < Foo.currentTime)
  }

  def testWithParameters(): Unit = {
    object Foo extends Managed {
      @Cached(synchronized = false, ModCount.getModificationCount, this)
      def currentTime(a: Int, b: Int): Long = System.currentTimeMillis()
    }

    val firstRes = Foo.currentTime(0, 0)
    Thread.sleep(1)
    Assert.assertEquals(firstRes, Foo.currentTime(0, 0))
    Assert.assertTrue(firstRes < Foo.currentTime(1, 0))
    Assert.assertTrue(firstRes < Foo.currentTime(0, 1))
    Foo.getManager.getModificationTracker.incCounter()
    val secondRes = Foo.currentTime(0, 0)
    Assert.assertTrue(firstRes < secondRes)
    Thread.sleep(1)
    Assert.assertEquals(secondRes, Foo.currentTime(0, 0))
  }
}
