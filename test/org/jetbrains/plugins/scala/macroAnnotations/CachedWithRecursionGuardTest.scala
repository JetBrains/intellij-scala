package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.psi.util.PsiModificationTracker
import org.junit.Assert

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/29/15.
 */
class CachedWithRecursionGuardTest extends CachedWithRecursionGuardTestBase{
  def testSimple(): Unit = {
    class Elem extends CachedMockPsiElement {
      @CachedWithRecursionGuard[Elem](this, Right("Failure"), PsiModificationTracker.MODIFICATION_COUNT)
      def recursiveFunction(d: Option[Int], depth: Int = 0): Either[Long, String] = {
        d match {
          case Some(l) => Right(l.toString)
          case _ if depth > 2 => Left(System.currentTimeMillis())
          case _ =>
            val res = recursiveFunction(None, depth)
            res
        }
      }
    }

    val elem = new Elem
    val firstRes: Either[Long, String] = elem.recursiveFunction(None, depth = 3)
    Assert.assertTrue(firstRes.isLeft)
    Thread.sleep(1)
    Assert.assertEquals(firstRes, elem.recursiveFunction(None, depth = 2))
    Assert.assertEquals(firstRes, elem.recursiveFunction(None))
  }
}
