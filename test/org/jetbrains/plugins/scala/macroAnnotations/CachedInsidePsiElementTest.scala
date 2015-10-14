package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.psi.util.PsiModificationTracker
import org.junit.Assert

/**
 * Author: Svyatoslav Ilinskiy
 * Date: 9/25/15.
 */
class CachedInsidePsiElementTest extends CachedWithRecursionGuardTestBase {

  def testSimple(): Unit = {
    object Foo extends CachedMockPsiElement {
      @CachedInsidePsiElement(this, PsiModificationTracker.MODIFICATION_COUNT)
      def currentTime(): Long = System.currentTimeMillis()
    }

    val firstRes: Long = Foo.currentTime()
    Thread.sleep(1)
    Assert.assertEquals(firstRes, Foo.currentTime())
  }
}
