package org.jetbrains.plugins.scala.refactoring.rename2

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.refactoring.rename.inplace.{ScalaInplaceRenameHandler, ScalaLocalInplaceRenameHandler, ScalaMemberInplaceRenameHandler}
import org.junit.Assert

class ScalaInplaceRenameHandlerTest extends ScalaFixtureTestCase {

  private def memberHandler = new ScalaMemberInplaceRenameHandler

  private def localHandler = new ScalaLocalInplaceRenameHandler

  private def isAvailable(handler: ScalaInplaceRenameHandler with VariableInplaceRenameHandler): Boolean = {
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PSI_ELEMENT, myFixture.getElementAtCaret)
      .add(CommonDataKeys.EDITOR, myFixture.getEditor)
      .add(CommonDataKeys.PSI_FILE, myFixture.getFile)
      .build()
    handler.isAvailableOnDataContext(dataContext)
  }

  private def checkIsAvailable(handler: ScalaInplaceRenameHandler with VariableInplaceRenameHandler): Unit = {
    Assert.assertTrue(s"$handler is not available", isAvailable(handler))
  }

  private def checkIsNotAvailable(handler: ScalaInplaceRenameHandler with VariableInplaceRenameHandler): Unit = {
    Assert.assertTrue(s"$handler is available", !isAvailable(handler))
  }

  private def checkIsLocalHandler(fileText: String): Unit = {
    myFixture.configureByText("dummy.scala", fileText.withNormalizedSeparator.trim)
    checkIsAvailable(localHandler)
    checkIsNotAvailable(memberHandler)
  }

  private def checkIsMemberHandler(fileText: String): Unit = {
    myFixture.configureByText("dummy.scala", fileText.withNormalizedSeparator.trim)
    checkIsAvailable(memberHandler)
    checkIsNotAvailable(localHandler)
  }

  def testLocal(): Unit = {
    checkIsLocalHandler(
      s"""
         |object Test {
         |  def foo(): Unit = {
         |    val ${CARET}x = 1
         |    x + x
         |  }
         |}
         |""".stripMargin)
  }

  def testLocalClass(): Unit = {
    checkIsLocalHandler(
      s"""object Test {
         |  def foo(): Unit = {
         |    class ${CARET}Abc
         |    class Bce extends Abc
         |  }
         |}
         |""".stripMargin)
  }

  def testClassParameter(): Unit = {
    checkIsMemberHandler(s"case class MyClass(${CARET}n: Int)")
  }

  def testParameter(): Unit = {
    checkIsMemberHandler(
      s"""
         |object Test {
         |  def foo(${CARET}bar: String): Unit = {
         |    println(bar)
         |  }
         |}
         |""".stripMargin)
  }

  def testMember(): Unit = {
    checkIsMemberHandler(
      s"""trait MyTrait {
         |  def ${CARET}doSomething: Unit
         |}
         |
         |case class MyClass(n: Int) extends MyTrait {
         |  override def doSomething: Unit = {}
         |}
         |""".stripMargin
    )
  }

  def testMemberInLocalScope(): Unit = {
    checkIsMemberHandler(
      s"""object Test {
         |
         |  def foo(): Unit = {
         |    trait MyTrait {
         |      def ${CARET}doSomething: Unit
         |    }
         |
         |    case class MyClass(n: Int) extends MyTrait {
         |      override def doSomething: Unit = {}
         |    }
         |  }
         |}""".stripMargin
    )
  }

}
