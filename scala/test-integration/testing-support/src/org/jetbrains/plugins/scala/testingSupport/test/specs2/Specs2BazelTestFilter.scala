package org.jetbrains.plugins.scala.testingSupport.test.specs2

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

import java.util.regex.Pattern

/**
 * Build a Bazel `--test_filter` regex for clicks on specs2 tests and scopes.
 *
 * Ported from the deleted Google IJwB Scala plugin (commit `f62b2670648d` of
 * `bazelbuild/intellij`, file `scala/src/com/google/idea/blaze/scala/run/Specs2Utils.java`).
 *
 * Filter format produced:
 *  - test ref (`"x" in {…}` / `>>` / `!`):  `<classFQN>#\Q<scope> should::<test text>\E$`
 *  - scope ref (`"x" should {…}` / `can`):  `<classFQN>#\Q<scope> should\E::`
 *
 * The trailing `$` (end-of-string anchor) on a test filter narrows to that one
 * test; the trailing `::` on a scope filter selects every test under the scope
 * (because the splitter prefixes each child).
 *
 * Precision is bounded by what Bazel's `JUnit4Runner` filter does with the
 * regex — for some specs2-junit / rules_scala combinations a click on one test
 * may also run same-named siblings from a different scope. That's acceptable
 * and matches the Google-era behaviour.
 *
 * @note Lives in `testing-support` rather than `intellij-bazel` because it is a
 *       pure PSI-to-string utility with no Bazel-runtime dependency, and
 *       `testing-support` already has the `ScalaFixtureTestCase` infrastructure
 *       used to unit-test it.
 */
object Specs2BazelTestFilter {

  /** Splitter between scope path and leaf test text in specs2-junit descriptions. */
  private val TestNamePartsSplitter = "::"

  def getContainingTestExprOrScope(element: PsiElement): Option[ScInfixExpr] =
    findContainingInfix(element, e => TestNodeProvider.isSpecs2TestExpr(e) || TestNodeProvider.isSpecs2ScopeExpr(e))

  def getContainingTestScope(element: PsiElement): Option[ScInfixExpr] =
    findContainingInfix(element, TestNodeProvider.isSpecs2ScopeExpr)

  private def findContainingInfix(element: PsiElement, predicate: PsiElement => Boolean): Option[ScInfixExpr] = {
    var current: PsiElement = element
    while (current != null && !predicate(current)) {
      current = PsiTreeUtil.getParentOfType(current, classOf[ScInfixExpr])
    }
    Option(current).collect { case infix: ScInfixExpr => infix }
  }

  /** For a scope `"x" should { … }` returns `"x should"`; `None` if the left-hand side isn't a static name. */
  def getSpecs2ScopeName(testScope: ScInfixExpr): Option[String] =
    staticTestName(testScope).map(name => s"$name ${testScope.operation.refName}")

  /** For a test `"x" in { … }` returns `"x"`, or `"<scope> should::x"` if it is inside a scope. */
  def getSpecs2ScopedTestName(testCase: ScInfixExpr): Option[String] =
    staticTestName(testCase).map { testName =>
      getContainingTestScope(testCase).flatMap(getSpecs2ScopeName) match {
        case Some(scopeName) => s"$scopeName$TestNamePartsSplitter$testName"
        case None            => testName
      }
    }

  /**
   * Build the Bazel `--test_filter` regex.
   *
   * @return `Some(regex)` if `element` is recognised as a specs2 test or scope
   *         with a static name; `None` if no usable name (e.g. interpolated string).
   */
  def getTestFilter(testClass: ScTypeDefinition, element: PsiElement): Option[String] = {
    val (rawName, suffix): (Option[String], String) =
      if (TestNodeProvider.isSpecs2TestExpr(element))
        (getSpecs2ScopedTestName(element.asInstanceOf[ScInfixExpr]), "$")
      else if (TestNodeProvider.isSpecs2ScopeExpr(element))
        (getSpecs2ScopeName(element.asInstanceOf[ScInfixExpr]), TestNamePartsSplitter)
      else
        (None, "")

    rawName.map { name =>
      // bazelbuild/intellij#176: parens in test names break the Bazel JUnit4 regex parser.
      val sanitized = name.trim.replace('(', '[').replace(')', ']')
      // bazelbuild/intellij#169: regex-escape everything else so user names are literal.
      s"${testClass.qualifiedName}#${Pattern.quote(sanitized)}$suffix"
    }
  }

  private def staticTestName(infix: ScInfixExpr): Option[String] = {
    val opt = TestConfigurationUtil.getStaticTestName(infix.getFirstChild)
    if (opt.isEmpty) None else Some(opt.get)
  }
}
