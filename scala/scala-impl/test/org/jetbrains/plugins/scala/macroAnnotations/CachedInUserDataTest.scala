package org.jetbrains.plugins.scala.macroAnnotations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.{ProjectUserDataHolder, cachedInUserData}
import org.junit.Assert._

// TODO currentTimeMillis -> { counter += 1; counter }
class CachedInUserDataTest extends CachedWithRecursionGuardTestBase {

  def testSimple(): Unit = {
    val element = new CachedMockPsiElement()

    def currentTime() = cachedInUserData("testSimple.currentTime", element, PsiModificationTracker.MODIFICATION_COUNT) {
      System.currentTimeMillis()
    }

    val firstRes: Long = currentTime()
    Thread.sleep(10)

    assertEquals(firstRes, currentTime())

    incModCount(getProject)

    assertNotEquals(firstRes, currentTime())
  }

  def testMultipleKeys(): Unit = {
    val element = new CachedMockPsiElement()

    val value1 = cachedInUserData("testMultipleKeys.method1", element, PsiModificationTracker.MODIFICATION_COUNT)(1)
    val value2 = cachedInUserData("testMultipleKeys.method2", element, PsiModificationTracker.MODIFICATION_COUNT)(2)

    assertNotEquals(value1, value2)
  }


  def testWithParameters(): Unit = {
    val element = new CachedMockPsiElement()

    def currentTime(s: String) = cachedInUserData("testWithParameters.currentTime", element, PsiModificationTracker.MODIFICATION_COUNT, Tuple1(s)) {
      System.currentTimeMillis()
    }

    val firstRes = currentTime("1")

    Thread.sleep(10)

    val secondRes = currentTime("2")

    assertTrue(firstRes < secondRes)

    assertEquals(firstRes, currentTime("1"))
    assertEquals(secondRes, currentTime("2"))

    incModCount(getProject)

    assertNotEquals(firstRes, currentTime("1"))
  }

  def testNotPsiElementHolder(): Unit = {
    implicit val fooHolder: ProjectUserDataHolder[Foo] = new ProjectUserDataHolder[Foo] {
      override def dataHolder(e: Foo): UserDataHolder = getProject
      override def project(e: Foo): Project = getProject
    }

    class Foo {
      def currentTime(): Long = cachedInUserData("testNotPsiElementHolder.currentTime", this, PsiModificationTracker.MODIFICATION_COUNT) {
        System.currentTimeMillis()
      }
    }

    val foo = new Foo
    val firstRes: Long = foo.currentTime()
    Thread.sleep(10)

    assertEquals(firstRes, foo.currentTime())

    incModCount(getProject)

    assertNotEquals(firstRes, foo.currentTime())
  }

  def testTracer(): Unit = {
    val element = new CachedMockPsiElement()

    def currentTime(): Long = cachedInUserData("testTracer.currentTime", element, PsiModificationTracker.MODIFICATION_COUNT) {
      System.currentTimeMillis()
    }

    checkTracer("CachedInUserDataTest.testTracer.currentTime", totalCount = 3, actualCount = 2) {
      currentTime()
      currentTime()

      incModCount(getProject)

      currentTime()
    }
  }

}
