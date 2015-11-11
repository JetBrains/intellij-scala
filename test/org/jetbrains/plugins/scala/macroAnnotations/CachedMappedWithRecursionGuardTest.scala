package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.psi.util.PsiModificationTracker
import org.junit.Assert

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/24/15.
 */
class CachedMappedWithRecursionGuardTest extends CachedWithRecursionGuardTestBase {
  def testRecursionGuard(): Unit = {
    object Elem extends CachedMockPsiElement {
      @CachedMappedWithRecursionGuard(this, "Failure", PsiModificationTracker.MODIFICATION_COUNT)
      def recursiveFunction(d: Option[Int], depth: Int = 0): String = {
        d match {
          case Some(l) => l.toString
          case _ if depth > 2 => "Blargle"
          case _ =>
            val res = recursiveFunction(None, depth)
            res
        }
      }
    }

    Assert.assertEquals("Blargle", Elem.recursiveFunction(None, depth = 3))
    Assert.assertEquals("Failure", Elem.recursiveFunction(None))
    Assert.assertEquals("123", Elem.recursiveFunction(Some(123), depth = 0))
  }
}
