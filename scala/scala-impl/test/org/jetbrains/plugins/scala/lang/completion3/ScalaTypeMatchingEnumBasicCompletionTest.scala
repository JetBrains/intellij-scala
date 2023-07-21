package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType.BASIC
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

abstract class ScalaTypeMatchingEnumBasicCompletionTest extends ScalaCompletionTestBase {

  private def configureJavaEnum(): Unit = myFixture.addFileToProject("completion/MyEnum.java",
    """
      |package completion;
      |
      |public enum MyEnum {
      |  NoConstructorCase
      |}
      |""".stripMargin
  )

  private def configureScala2Enum(): Unit = myFixture.addFileToProject("completion/MyEnum.scala",
    """
      |package completion
      |
      |object MyEnum extends Enumeration {
      |  type MyEnum = Value
      |  val NoConstructorCase = Value
      |}
      |""".stripMargin
  )

  /// ------------ JAVA ENUM -------------

  def testJavaEnumInMatchByCaseName(): Unit = {
    configureJavaEnum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case NoC$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case MyEnum.NoConstructorCase$CARET
           |  }
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testJavaEnumInMatchByEnumName(): Unit = {
    configureJavaEnum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case My$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case MyEnum.NoConstructorCase$CARET
           |  }
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testJavaEnumInValInitialization(): Unit = {
    configureJavaEnum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = My$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = MyEnum.NoConstructorCase$CARET
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testJavaEnumInMethodCall(): Unit = {
    configureJavaEnum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def doSomething(myEnum: MyEnum): Unit = {}
           |
           |  doSomething(No$CARET)
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def doSomething(myEnum: MyEnum): Unit = {}
           |
           |  doSomething(MyEnum.NoConstructorCase$CARET)
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testJavaEnumInMethodCallWithNamedArgument(): Unit = {
    configureJavaEnum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def doSomething(flag: Boolean, myEnum: MyEnum): Unit = {}
           |
           |  doSomething(myEnum = No$CARET)
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def doSomething(flag: Boolean, myEnum: MyEnum): Unit = {}
           |
           |  doSomething(myEnum = MyEnum.NoConstructorCase$CARET)
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  /// ------------ JAVA ENUM -------------

  /// ---------- SCALA 2 ENUM ------------

  def testScala2EnumInMatchByCaseName(): Unit = {
    configureScala2Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case NoC$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case MyEnum.NoConstructorCase$CARET
           |  }
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala2EnumInMatchByEnumName(): Unit = {
    configureScala2Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case My$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case MyEnum.NoConstructorCase$CARET
           |  }
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala2EnumInValInitialization(): Unit = {
    configureScala2Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = My$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = MyEnum.NoConstructorCase$CARET
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala2EnumInMethodCall(): Unit = {
    configureScala2Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def doSomething(myEnum: MyEnum): Unit = {}
           |
           |  doSomething(No$CARET)
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def doSomething(myEnum: MyEnum): Unit = {}
           |
           |  doSomething(MyEnum.NoConstructorCase$CARET)
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala2EnumInMethodCallWithNamedArgument(): Unit = {
    configureScala2Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def doSomething(flag: Boolean, myEnum: MyEnum): Unit = {}
           |
           |  doSomething(myEnum = No$CARET)
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.MyEnum
           |
           |object Test {
           |  def doSomething(flag: Boolean, myEnum: MyEnum): Unit = {}
           |
           |  doSomething(myEnum = MyEnum.NoConstructorCase$CARET)
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  /// ---------- SCALA 2 ENUM ------------
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
final class ScalaTypeMatchingEnumBasicCompletionTest_2_13 extends ScalaTypeMatchingEnumBasicCompletionTest

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
final class ScalaTypeMatchingEnumBasicCompletionTest_3_Latest extends ScalaTypeMatchingEnumBasicCompletionTest {

  // TODO: ERROR: Tree access disabled in org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.EnumMembersInjector#injectEnumCase
  private def withoutAstLoadingFilter(action: => Unit): Unit = {
    val oldValue = Registry.get("ast.loading.filter").asBoolean()
    try {
      Registry.get("ast.loading.filter").setValue(false)
      action
    } finally Registry.get("ast.loading.filter").setValue(oldValue)
  }

  private def configureScala3Enum(): Unit = myFixture.addFileToProject("completion/MyEnum.scala",
    """
      |package completion
      |
      |enum MyEnum {
      |  case TypedCase[T](value: T)
      |  case NoArgCase()
      |  case OneArgCase(arg: String)
      |  case NoConstructorCase
      |}
      |""".stripMargin
  )

  /// ---------- SCALA 3 ENUM ------------

  def testScala3EnumInMatchByCaseName(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case NoC$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.NoConstructorCase
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case NoConstructorCase$CARET
           |  }
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInMatchByEnumName(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case My$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.NoConstructorCase
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case NoConstructorCase$CARET
           |  }
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInMatchWithTypeParams(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case Ty$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.TypedCase
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case TypedCase($CARET)
           |  }
           |}
           |""".stripMargin,
      item = "TypedCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInMatchWithConstructorWithoutParams(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case No$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.NoArgCase
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case NoArgCase($CARET)
           |  }
           |}
           |""".stripMargin,
      item = "NoArgCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInMatchWithConstructorWithParams(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case On$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.OneArgCase
           |
           |object Test {
           |  def test(myEnum: MyEnum): Unit = myEnum match {
           |    case OneArgCase($CARET)
           |  }
           |}
           |""".stripMargin,
      item = "OneArgCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInValInitialization(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = My$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.NoConstructorCase
           |
           |object Test {
           |  val myEnum: MyEnum = NoConstructorCase$CARET
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInValInitializationWithTypeParams(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = My$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.TypedCase
           |
           |object Test {
           |  val myEnum: MyEnum = TypedCase($CARET)
           |}
           |""".stripMargin,
      item = "TypedCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInValInitializationWithConstructorWithoutParams(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = My$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.NoArgCase
           |
           |object Test {
           |  val myEnum: MyEnum = NoArgCase($CARET)
           |}
           |""".stripMargin,
      item = "NoArgCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInValInitializationWithConstructorWithParams(): Unit = withoutAstLoadingFilter {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  val myEnum: MyEnum = My$CARET
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.OneArgCase
           |
           |object Test {
           |  val myEnum: MyEnum = OneArgCase($CARET)
           |}
           |""".stripMargin,
      item = "OneArgCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInMethodCall(): Unit = {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def doSomething(myEnum: MyEnum): Unit = {}
           |
           |  doSomething(No$CARET)
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.NoConstructorCase
           |
           |object Test {
           |  def doSomething(myEnum: MyEnum): Unit = {}
           |
           |  doSomething(NoConstructorCase$CARET)
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  def testScala3EnumInMethodCallWithNamedArgument(): Unit = {
    configureScala3Enum()

    doCompletionTest(
      fileText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |
           |object Test {
           |  def doSomething(flag: Boolean, myEnum: MyEnum): Unit = {}
           |
           |  doSomething(myEnum = No$CARET)
           |}
           |""".stripMargin,
      resultText =
        s"""
           |package tests
           |
           |import completion.MyEnum
           |import completion.MyEnum.NoConstructorCase
           |
           |object Test {
           |  def doSomething(flag: Boolean, myEnum: MyEnum): Unit = {}
           |
           |  doSomething(myEnum = NoConstructorCase$CARET)
           |}
           |""".stripMargin,
      item = "NoConstructorCase",
      completionType = BASIC
    )
  }

  /// ---------- SCALA 3 ENUM ------------
}
