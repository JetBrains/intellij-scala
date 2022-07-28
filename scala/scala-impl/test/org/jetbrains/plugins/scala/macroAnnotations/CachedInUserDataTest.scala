package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.ProjectUserDataHolder
import org.junit.Assert._

class CachedInUserDataTest extends CachedWithRecursionGuardTestBase {

  def testSimple(): Unit = {
    object Foo extends CachedMockPsiElement {
      @CachedInUserData(this, PsiModificationTracker.MODIFICATION_COUNT)
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
      @CachedInUserData(this, PsiModificationTracker.MODIFICATION_COUNT)
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

  def testNotPsiElementHolder(): Unit = {
    implicit val fooHolder: ProjectUserDataHolder[Foo] = new ProjectUserDataHolder[Foo] {
      override def dataHolder(e: Foo): UserDataHolder = getProject
      override def project(e: Foo): Project = getProject
    }

    class Foo {
      @CachedInUserData(this, PsiModificationTracker.MODIFICATION_COUNT)
      def currentTime(): Long = System.currentTimeMillis()
    }

    val foo = new Foo
    val firstRes: Long = foo.currentTime()
    Thread.sleep(10)

    assertEquals(firstRes, foo.currentTime())

    incModCount(getProject)

    assertNotEquals(firstRes, foo.currentTime())
  }

  def testTracer(): Unit = {
    object Foo extends CachedMockPsiElement {
      @CachedInUserData(this, PsiModificationTracker.MODIFICATION_COUNT)
      def currentTime(): Long = System.currentTimeMillis()
    }

    checkTracer("Foo.currentTime", totalCount = 3, actualCount = 2) {
      Foo.currentTime()
      Foo.currentTime()

      incModCount(getProject)

      Foo.currentTime()
    }
  }

  def testTracerWithExpr(): Unit = {
    class Foo extends CachedMockPsiElement {
      @CachedInUserData(this, PsiModificationTracker.MODIFICATION_COUNT, int)
      def twice(int: Int): Int = int * 2
    }

    checkTracer("Foo.twice int == 1", totalCount = 3, actualCount = 2) {
      val foo = new Foo
      foo.twice(1)
      foo.twice(1)
      foo.twice(2)
      foo.twice(2)

      incModCount(getProject)

      foo.twice(1)
    }

    checkTracer("Foo.twice int == 2", totalCount = 2, actualCount = 1) {
      val foo = new Foo
      foo.twice(1)
      foo.twice(1)
      foo.twice(2)
      foo.twice(2)

      incModCount(getProject)

      foo.twice(1)
    }

  }

}
