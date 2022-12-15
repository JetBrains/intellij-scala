package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.{ProjectUserDataHolder, cachedInUserData}
import org.junit.Assert._

class CachedInUserDataTest extends CachedWithRecursionGuardTestBase {

  def testSimple(): Unit = {
    object Foo extends CachedMockPsiElement {
      def currentTime(): java.lang.Long = cachedInUserData("Foo.currentTime", this, PsiModificationTracker.MODIFICATION_COUNT, {
        System.currentTimeMillis()
      })
    }

    val firstRes: Long = Foo.currentTime()
    Thread.sleep(10)

    assertEquals(firstRes, Foo.currentTime())

    incModCount(getProject)

    assertNotEquals(firstRes, Foo.currentTime())
  }

  def testWithParameters(): Unit = {
    object Foo extends CachedMockPsiElement {
      def currentTime(s: String): java.lang.Long = cachedInUserData("Foo.currentTime", this, PsiModificationTracker.MODIFICATION_COUNT, Tuple1(s), {
        System.currentTimeMillis()
      })
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
      def currentTime(): java.lang.Long = cachedInUserData("Foo.currentTime", this, PsiModificationTracker.MODIFICATION_COUNT, {
        System.currentTimeMillis()
      })
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
      def currentTime(): java.lang.Long = cachedInUserData("Foo.currentTime", this, PsiModificationTracker.MODIFICATION_COUNT, {
        System.currentTimeMillis()
      })
    }

    checkTracer("Foo.currentTime", totalCount = 3, actualCount = 2) {
      Foo.currentTime()
      Foo.currentTime()

      incModCount(getProject)

      Foo.currentTime()
    }
  }

}
