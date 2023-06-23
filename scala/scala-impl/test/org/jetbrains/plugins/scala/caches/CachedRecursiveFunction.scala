package org.jetbrains.plugins.scala.caches

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

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
case class CachedRecursiveFunction(name: String)(implicit projectContext: ProjectContext) {
  assert(Seq("#", "@", "+").forall(!name.contains(_)))

  private[this] val psi =
    PsiSelectionUtil.selectElement[PsiElement](
      ScalaPsiElementFactory.createScalaFileFromText("class Test", ScalaFeatures.default)(projectContext),
      PsiSelectionUtil.path("Test")
    )
  private[this] var innerCalls = Seq.empty[CachedRecursiveFunction]
  private[this] var calcCounter = 0

  def ~>(innerCall: CachedRecursiveFunction): innerCall.type = {
    innerCalls :+= innerCall
    innerCall
  }

  def apply(): String = {
    val oldCount = calcCounter
    val result = cachedWithRecursionGuard("apply.result", psi, "#" + name, ModTracker.physicalPsiChange(psi.getProject)) {
      calcCounter += 1
      innerCalls.map(_.apply()).mkString(name + "(", "+", ")")
    }

    val wasCached = oldCount == calcCounter && !result.startsWith("#")

    if (wasCached) {
      import RecursionManager.RecursionGuard
      val functionClassName = classOf[CachedRecursiveFunction].getName.replace('.', '$')
      val Seq(id) = RecursionGuard.ids.filter(_.startsWith(functionClassName)).toSeq
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