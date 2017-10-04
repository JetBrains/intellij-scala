package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.psi.util.PsiModificationTracker
import org.junit.Assert._

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
    Thread.sleep(10)

    assertEquals(firstRes, Foo.currentTime())

    incModCount(getProject)

    assertNotEquals(firstRes, Foo.currentTime())
  }

  def testWithParameters(): Unit = {
    object Foo extends CachedMockPsiElement {
      @CachedInsidePsiElement(this, PsiModificationTracker.MODIFICATION_COUNT)
      def currentTime(s: String): Long = System.currentTimeMillis()
    }

    val firstRes = Foo.currentTime("1")

    Thread.sleep(10)

    val secondRes = Foo.currentTime("2")

    assertTrue(firstRes < secondRes)

    assertEquals(firstRes, Foo.currentTime("1"))
    assertEquals(secondRes, Foo.currentTime("2"))

    incModCount(getProject)

    assertNotEquals(firstRes, Foo.currentTime("1"))
  }
}
