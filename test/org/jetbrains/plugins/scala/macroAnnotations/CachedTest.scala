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
      @Cached(synchronized = false)
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

      //volatile should not be needed, since the main thread waits for all others to finish, but let's keep it here for extra safety
      @volatile
      var assertsFailed = 0

      val allThreadsStartedLock: ReentrantLock = new ReentrantLock()

      @Cached(synchronized = true)
      def runSyncronized(): Unit = {
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
        override def run(): Unit = Foo.runSyncronized()
      })
      val thread2 = new Thread(new Runnable {
        override def run(): Unit = Foo.runSyncronized()
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
      thread2.start() //this thread should be waiting outside synchronize block
      Foo.allThreadsStartedLock.unlock()
      (thread1, thread2)
    }

    val (t1, t2) = setUpThreads()
    t1.join()
    t2.join()
    Assert.assertEquals(0, Foo.assertsFailed)
  }
}
