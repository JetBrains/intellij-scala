package org.jetbrains.plugins.scala.lang.psi.scope

import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

class GetUseScopeTest extends SimpleTestCase {
  
  private def doTest(fileText: String, scopeAssertion: SearchScope => Unit): Unit = {
    val (file, offset) = parseText(normalize(fileText), CARET_TAG)
    val named = file.findElementAt(offset).parentOfType(Seq(classOf[ScNamedElement])).get
    scopeAssertion(named.getUseScope)
  }

  private def assertIsLocal(fileText: String): Unit =
    doTest(fileText, scope =>
      assert(scope.isInstanceOf[LocalSearchScope], s"Local scope expected, was: $scope")
    )

  private def assertIsNotLocal(fileText: String): Unit =
    doTest(fileText, scope =>
      assert(!scope.isInstanceOf[LocalSearchScope], s"Scope should not be local, was: $scope")
    )


  def testLocalVariable(): Unit = assertIsLocal(
    s"""
      |object ABC {
      |  def foo(): Int = {
      |    val ${CARET_TAG}x = 1
      |    x + x
      |  }
      |}""")

  def testPrivateMethod(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  private def ${CARET_TAG}foo(i: Int): Int = {
       |    val x = 1
       |    x + i
       |  }
       |}""")

  def testPrivateThisMethod(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  private[this] def ${CARET_TAG}foo(i: Int): Int = {
       |    val x = 1
       |    x + i
       |  }
       |}""")


  def testPrivateMethodParameter(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  private def foo(${CARET_TAG}i: Int): Int = {
       |    val x = 1
       |    x + i
       |  }
       |}""")

  def testPrivateMemberClass(): Unit = assertIsLocal(
    s"""object ABC {
       |  private class ${CARET_TAG}D
       |}
    """
  )

  def testPrivateClassParameter(): Unit = assertIsLocal(
    s"class ABC(private val ${CARET_TAG}a: Int)"
  )

  def testPattern(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  val x = 123 match {
       |    case ${CARET_TAG}i => i
       |  }
       |}""")

  def testTopLevelObject(): Unit = assertIsNotLocal(
    s"""
       |object ${CARET_TAG}ABC {
       |
       |}""")

  def testPublic(): Unit = assertIsNotLocal(
    s"""
       |object ABC {
       |  def ${CARET_TAG}abc() = 1
       |}""")

  def testClassParameter(): Unit = assertIsNotLocal(
    s"class ABC(val ${CARET_TAG}a: Int)"
  )
}
