package org.jetbrains.plugins.scala
package caches

import com.intellij.mock.MockPsiElement
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, incModCount}
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

class CacheModeTest extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers {
  sealed trait TestResult
  case object NotComputed extends TestResult
  case object ComputedOnce extends TestResult
  case object ComputedTwice extends TestResult

  abstract class TestPsiElement extends MockPsiElement(getProject) {
    override def getProject: Project = myFixture.getProject
    override def getParent: Null = null

    def test(cacheMode: CacheMode[TestResult]): TestResult
    def test(arg: Boolean, cacheMode: CacheMode[TestResult]): TestResult

    private var timesComputed = 0
    protected def compute(): TestResult = {
      assert(timesComputed <= 2)
      timesComputed += 1
      timesComputed match {
        case 1 => ComputedOnce
        case 2 => ComputedTwice
      }
    }
    def makeOutdated(): Unit = incModCount(getProject)
  }

  class TestCachedMacroElement extends TestPsiElement {
    @Cached(ModTracker.physicalPsiChange(getProject), this)
    override def test(cacheMode: CacheMode[TestResult]): TestResult = compute()

    @Cached(ModTracker.physicalPsiChange(getProject), this)
    override def test(arg: Boolean, cacheMode: CacheMode[TestResult]): TestResult = {
      assert(arg == arg)
      compute()
    }
  }

  /*
    class TestCacheInUserdataMacroElement extends TestPsiElement {
      @CachedInUserData(this, ModTracker.physicalPsiChange)
      override def test(cacheMode: CacheMode[TestResult]): TestResult = compute()

      @CachedInUserData(this, ModTracker.physicalPsiChange)
      override def test(arg: Boolean, cacheMode: CacheMode[TestResult]): TestResult = compute()
    }
  */


  val elementFactories = Seq(
    () => new TestCachedMacroElement,
  )
  val testFs = Seq(
    (elem: TestPsiElement, cm: CacheMode[TestResult]) => elem.test(cm),
    (elem: TestPsiElement, cm: CacheMode[TestResult]) => elem.test(arg = false, cm),
    (elem: TestPsiElement, cm: CacheMode[TestResult]) => elem.test(arg = true, cm),
  )

  def test_all_combinations(): Unit = {
    for {
      createElement <- elementFactories
      testF <- testFs
    } {
      val element = createElement()
      doTestElement(element, testF)
    }
  }

  def doTestElement(elem: TestPsiElement, f: (TestPsiElement, CacheMode[TestResult]) => TestResult): Unit = {
    f(elem, CacheMode.CachedOrDefault(NotComputed)) shouldBe NotComputed
    f(elem, CacheMode.CachedOrDefault(NotComputed)) shouldBe NotComputed
    f(elem, CacheMode.ComputeIfNotCached)           shouldBe ComputedOnce
    f(elem, CacheMode.ComputeIfOutdated)            shouldBe ComputedOnce
    f(elem, CacheMode.CachedOrDefault(NotComputed)) shouldBe ComputedOnce
    elem.makeOutdated()
    f(elem, CacheMode.CachedOrDefault(NotComputed)) shouldBe ComputedOnce
    f(elem, CacheMode.ComputeIfNotCached)           shouldBe ComputedOnce
    f(elem, CacheMode.ComputeIfOutdated)            shouldBe ComputedTwice
    f(elem, CacheMode.ComputeIfNotCached)           shouldBe ComputedTwice
  }
}
