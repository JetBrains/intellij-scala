package org.jetbrains.plugins.scala.lang.psi.scope

import com.intellij.psi.PsiElement
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.testFramework.EditorTestUtil.CARET_TAG
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

class GetUseScopeTest extends SimpleTestCase {
  
  private def doTest(fileText: String)(scopeAssertion: (ScNamedElement, SearchScope) => Unit): Unit = {
    val (file, offset) = parseScalaFileAndGetCaretPosition(fileText.withNormalizedSeparator.trim, CARET_TAG)
    val named = file.findElementAt(offset).parentOfType(classOf[ScNamedElement]).get
    scopeAssertion(named, named.getUseScope)
  }

  private def assertIsLocal(fileText: String): Unit =
    doTest(fileText)((_, scope) =>
      assert(scope.isInstanceOf[LocalSearchScope], s"Local scope expected, was: $scope")
    )

  private def assertIsNotLocal(fileText: String): Unit =
    doTest(fileText)((_, scope) =>
      assert(!scope.isInstanceOf[LocalSearchScope], s"Scope should not be local, was: $scope")
    )

  private def assertLocalScopeContains(fileText: String)(psiFunction: ScNamedElement => PsiElement): Unit = {
    doTest(fileText)((named, scope) => {
      assert(scope.isInstanceOf[LocalSearchScope], s"Local scope expected, was: $scope")
      val element = psiFunction(named)
      assert(scope.asInstanceOf[LocalSearchScope].containsRange(element.getContainingFile, element.getTextRange))
    })
  }

  def testLocalVariable(): Unit = assertIsLocal(
    s"""
      |object ABC {
      |  def foo(): Int = {
      |    val ${CARET_TAG}x = 1
      |    x + x
      |  }
      |}""".stripMargin)

  def testPrivateMethod(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  private def ${CARET_TAG}foo(i: Int): Int = {
       |    val x = 1
       |    x + i
       |  }
       |}""".stripMargin)

  def testPrivateThisMethod(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  private[this] def ${CARET_TAG}foo(i: Int): Int = {
       |    val x = 1
       |    x + i
       |  }
       |}""".stripMargin)


  def testPrivateMethodParameter(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  private def foo(${CARET_TAG}i: Int): Int = {
       |    val x = 1
       |    x + i
       |  }
       |}""".stripMargin)

  def testPrivateMemberClass(): Unit = assertIsLocal(
    s"""object ABC {
       |  private class ${CARET_TAG}D
       |}
    """.stripMargin
  )

  //may be used in named arguments
  def testPrivateClassParameter(): Unit = assertIsNotLocal(
    s"class ABC(private val ${CARET_TAG}classParam: Int)"
  )

  //may be used in named arguments
  def testPrivateThisClassParameter(): Unit = assertIsNotLocal(
    s"class ABC(private[this] val ${CARET_TAG}classParam: Int)"
  )

  def testPattern(): Unit = assertIsLocal(
    s"""
       |object ABC {
       |  val x = 123 match {
       |    case ${CARET_TAG}i => i
       |  }
       |}""".stripMargin)

  def testTopLevelObject(): Unit = assertIsNotLocal(
    s"""
       |object ${CARET_TAG}ABC {
       |
       |}""".stripMargin)

  def testPublic(): Unit = assertIsNotLocal(
    s"""
       |object ABC {
       |  def ${CARET_TAG}abc() = 1
       |}""".stripMargin)

  def testClassParameter(): Unit = assertIsNotLocal(
    s"class ABC(val ${CARET_TAG}a: Int)"
  )

  def testClassParameterNotMember(): Unit = assertIsNotLocal(
    s"class ABC(${CARET_TAG}classParam: Int)"
  )

  def testInnerPrivateClassMember(): Unit = {
    val code =
      s"""
        |object Test {
        |  private class A {
        |    def ${CARET_TAG}foo() = 1
        |  }
        |}
      """.stripMargin

    assertLocalScopeContains(code) { named =>
      val containingClass = named.asInstanceOf[ScFunction].containingClass
      val topLevelObject = containingClass.getContainingClass
      topLevelObject
    }
  }
}
