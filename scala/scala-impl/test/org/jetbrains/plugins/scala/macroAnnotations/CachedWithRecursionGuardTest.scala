package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.cachedWithRecursionGuard
import org.junit.Assert._

import java.util.concurrent.atomic.AtomicInteger

class CachedWithRecursionGuardTest extends CachedWithRecursionGuardTestBase {
  def testWithoutParameters(): Unit = {
    class Elem extends CachedMockPsiElement {
      var depth = 0
      def recursiveFunction: Either[Long, String] = cachedWithRecursionGuard("recursiveFunction", this, Right("Failure"): Either[Long, String], PsiModificationTracker.MODIFICATION_COUNT) {
        if (depth > 0) recursiveFunction
        else Left(System.currentTimeMillis())
      }
    }

    val elem = new Elem
    val firstRes: Either[Long, String] = elem.recursiveFunction
    assertTrue(firstRes.isLeft)
    elem.depth = 1
    assertEquals(firstRes, elem.recursiveFunction)

    incModCount(getProject)

    assertEquals(Right("Failure"), elem.recursiveFunction)

    elem.depth = 0
    val secondRes = elem.recursiveFunction

    assertNotEquals(firstRes, secondRes)
    assertEquals(secondRes, elem.recursiveFunction)
  }

  def testMultipleKeys(): Unit = {
    val element = new CachedMockPsiElement()

    val value1 = cachedWithRecursionGuard("testMultipleKeys.value1", element, 0, PsiModificationTracker.MODIFICATION_COUNT)(1)
    val value2 = cachedWithRecursionGuard("testMultipleKeys.value2", element, 0, PsiModificationTracker.MODIFICATION_COUNT)(2)

    assertNotEquals(value1, value2)
  }

  def testWithParameters(): Unit = {
    object Elem extends CachedMockPsiElement {
      val counter = new AtomicInteger(0)

      def recursiveFunction(d: Option[Int], depth: Int = 0): String = cachedWithRecursionGuard("recursiveFunction", this, "Failure", PsiModificationTracker.MODIFICATION_COUNT, (d, depth)) {
        d match {
          case Some(value) => (counter.getAndIncrement() + value).toString
          case _ if depth > 2 => "Blargle"
          case _ =>
            val res = recursiveFunction(None, depth)
            res
        }
      }
    }

    assertEquals("Blargle", Elem.recursiveFunction(None, depth = 3))
    assertEquals("Failure", Elem.recursiveFunction(None))
    assertEquals("1", Elem.recursiveFunction(Some(1)))
    assertEquals("1", Elem.recursiveFunction(Some(1)))

    incModCount(getProject)

    assertEquals("2", Elem.recursiveFunction(Some(1)))
  }

  def testTracer(): Unit = {
    class Elem extends CachedMockPsiElement {
      def rec(isRecursive: Boolean): Either[Long, String] = cachedWithRecursionGuard("rec", this, Right("Failure"): Either[Long, String], PsiModificationTracker.MODIFICATION_COUNT, Tuple1(isRecursive)) {
        if (isRecursive) rec(isRecursive)
        else Left(System.currentTimeMillis())
      }
    }

    checkTracer("CachedWithRecursionGuardTest$Elem$4.rec", totalCount = 5, actualCount = 2) {

      val elem = new Elem
      elem.rec(true)
      elem.rec(true)

      elem.rec(false)
      elem.rec(false)
    }
  }
}
