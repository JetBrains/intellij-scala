package org.jetbrains.plugins.scala.caches

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.TestFixtureProvider
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

trait CacheTestUtils { testFixtureProvider: TestFixtureProvider =>
  /**
   * Represents a function that may call multiple other functions which might ultimately end up in a recursion.
   *
   * @param name of the function
   *
   * Functions can be connected by calls via the ~> operator
   *
   * 1. x calls only y:
   *    x ~> y
   *
   * 2. x calls y and y calls x
   *    x ~> y ~> x // is equivalent to x ~> y; y ~> x
   *
   * 3. x calls y first and after y returned it calls z
   *    x ~> y
   *    x ~> z
   *
   * @note that all functions have an internal state, so make sure to create fresh ones for each test
   *
   * Evaluate a function by calling its apply method.
   * The result is a tree of which functions were evaluated in what order.
   * The tree is represented as a string
   *    "x()"          // means x didn't call any other functions
   *    "x(y()+z())"   // means x called y and after that z (but y and z didn't call any other functions)
   *    "x(y(#x))"     // # denotes a call to a function that is currently being evaluated, so a default value is returned
   *    "@x()"         // @ denotes that x was not evaluated but the result was taken from its cache
   *    "@@x()"        // @@ denotes that x was not evaluated and not normally cached, but was taken from the local cache
   *
   */
  case class CachedRecursiveFunction(name: String) {
    assert(Seq("#", "@", "+").forall(!name.contains(_)))

    private[this] val psi = makePsiElement()
    private[this] var innerCalls = Seq.empty[CachedRecursiveFunction]
    private[this] var calcCounter = 0

    def ~>(innerCall: CachedRecursiveFunction): innerCall.type = {
      innerCalls :+= innerCall
      innerCall
    }

    @CachedWithRecursionGuard(psi, "#" + name, ModTracker.physicalPsiChange(psi.getProject))
    private[this] def internal_cached_call(): String = {
      calcCounter += 1
      innerCalls.map(_.apply()).mkString(name + "(", "+", ")")
    }

    def apply(): String = {
      val oldCount = calcCounter
      val result = internal_cached_call()

      val wasCached = oldCount == calcCounter && !result.startsWith("#")

      if (wasCached) {
        import RecursionManager.RecursionGuard
        val Seq(id) = RecursionGuard.allGuardNames.filter(_.contains("internal_cached_call")).toSeq
        val localResult = RecursionGuard[PsiElement, String](id).getFromLocalCache(psi)
        if (localResult == null) {
          "@" + result
        } else {
          "@@" + result
        }
      }
      else result
    }
  }

  private def makePsiElement(): PsiElement = {
    implicit val projectContext: ProjectContext = testFixtureProvider.getFixture.getProject
    import PsiSelectionUtil._

    val file = ScalaPsiElementFactory.createScalaFileFromText("class Test")
    selectElement[PsiElement](file, path("Test"))
  }

}
