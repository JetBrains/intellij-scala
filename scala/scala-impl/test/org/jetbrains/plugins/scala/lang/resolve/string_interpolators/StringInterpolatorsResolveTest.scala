package org.jetbrains.plugins.scala.lang.resolve.string_interpolators

import com.intellij.psi.PsiReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveTestCase
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions.assertIsA
import org.junit.Assert._

class StringInterpolatorsResolveTest extends ScalaResolveTestCase {

  override def folderPath: String = s"${super.folderPath}resolve/string_interpolators"

  override def sourceRootPath: String = folderPath

  def testRawInterpolator(): Unit =
    doTestDefaultInterpolator("raw")

  def testSInterpolator(): Unit =
    doTestDefaultInterpolator("s")

  private def doTestDefaultInterpolator(name: String): Unit = {
    val references = findAllReferencesAtCarets
    assertTrue(references.size > 1)

    references.zipWithIndex.foreach { case (ref0, idx) =>
      doTestInterpolatorReference(name, ref0, idx)
    }
  }

  private def doTestInterpolatorReference(name: String, ref0: PsiReference, refIdx: Int): Unit = try {
    val ref = assertIsA[ScInterpolatedExpressionPrefix](ref0)
    val resolvedArray = ref.multiResolveScala(false)
    assertTrue(resolvedArray.length == 1)

    val resolved = resolvedArray.head
    val fun = assertIsA[ScFunctionDefinition](resolved.get.element)
    assertEquals(name, fun.name)

    val containingClass = fun.containingClass
    assertEquals("scala.StringContext", containingClass.qualifiedName)
  } catch {
    case error: AssertionError =>
      System.err.println(s"Assertion error for reference number $refIdx")
      throw error
  }
}
