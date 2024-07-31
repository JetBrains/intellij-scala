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

  private def configureJavaWeekdayEnum(): Unit = myFixture.addFileToProject("completion/WeekDayJava.java",
    """
      |package completion
      |
      |public enum WeekDayJava {
      |    Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
      |}
      |""".stripMargin
  )

  private def configureScala2WeekdayEnum(): Unit = myFixture.addFileToProject("completion/WeekDayScala2Enumeration.scala",
    """
      |package completion
      |
      |object WeekDayScala2Enumeration extends Enumeration {
      |  type WeekDay = Value
      |  val Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday = Value
      |}
      |""".stripMargin
  )

  private def configureScalaSealedWeekdayEnum(): Unit = myFixture.addFileToProject("completion/WeekDayScala2ADT.scala",
    """
      |package completion
      |
      |sealed trait WeekDayScala2ADT
      |object WeekDayScala2ADT {
      |  object Monday extends WeekDayScala2ADT
      |  object Tuesday extends WeekDayScala2ADT
      |  object Wednesday extends WeekDayScala2ADT
      |  object Thursday extends WeekDayScala2ADT
      |  object Friday extends WeekDayScala2ADT
      |  object Saturday extends WeekDayScala2ADT
      |  object Sunday extends WeekDayScala2ADT
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

  def testSCL_21582_JavaEnum_VariableDefinition(): Unit = {
    configureJavaWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayJava = F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayJava = WeekDayJava.Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_JavaEnum_VariableDefinition_Seq(): Unit = {
    configureJavaWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayJava] = Seq($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayJava] = Seq(WeekDayJava.Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_JavaEnum_Equals(): Unit = {
    configureJavaWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayJava = ???
           |
           |    enumsInstance == F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayJava = ???
          |
          |    enumsInstance == WeekDayJava.Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_JavaEnum_Equals_InMethodCall(): Unit = {
    configureJavaWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayJava] = ???
           |
           |    enumsSeq.exists(_ == $CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayJava] = ???
          |
          |    enumsSeq.exists(_ == WeekDayJava.Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_JavaEnum_MethodCall(): Unit = {
    configureJavaWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayJava] = ???
           |
           |    enumsSeq.contains($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayJava] = ???
          |
          |    enumsSeq.contains(WeekDayJava.Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_JavaEnum_MethodCall2(): Unit = {
    configureJavaWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    foo($CARET)
           |  }
           |
           |  def foo(p: WeekDayJava): Unit = ???
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    foo(WeekDayJava.Friday)
          |  }
          |
          |  def foo(p: WeekDayJava): Unit = ???
          |}
          |""".stripMargin,
      item = "Friday",
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

  def testSCL_21582_Scala2Enum_VariableDefinition(): Unit = {
    configureScala2WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayScala2Enumeration.WeekDay = F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayScala2Enumeration.WeekDay = WeekDayScala2Enumeration.Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala2Enum_VariableDefinition_Seq(): Unit = {
    configureScala2WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala2Enumeration.WeekDay] = Seq($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala2Enumeration.WeekDay] = Seq(WeekDayScala2Enumeration.Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala2Enum_Equals(): Unit = {
    configureScala2WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayScala2Enumeration.WeekDay = ???
           |
           |    enumsInstance == F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayScala2Enumeration.WeekDay = ???
          |
          |    enumsInstance == WeekDayScala2Enumeration.Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala2Enum_Equals_InMethodCall(): Unit = {
    configureScala2WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala2Enumeration.WeekDay] = ???
           |
           |    enumsSeq.exists(_ == $CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala2Enumeration.WeekDay] = ???
          |
          |    enumsSeq.exists(_ == WeekDayScala2Enumeration.Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala2Enum_MethodCall(): Unit = {
    configureScala2WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala2Enumeration.WeekDay] = ???
           |
           |    enumsSeq.contains($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala2Enumeration.WeekDay] = ???
          |
          |    enumsSeq.contains(WeekDayScala2Enumeration.Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala2Enum_MethodCall2(): Unit = {
    configureScala2WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    foo($CARET)
           |  }
           |
           |  def foo(p: WeekDayScala2Enumeration.WeekDay): Unit = ???
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    foo(WeekDayScala2Enumeration.Friday)
          |  }
          |
          |  def foo(p: WeekDayScala2Enumeration.WeekDay): Unit = ???
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  /// ---------- SCALA 2 ENUM ------------

  /// ---------- SCALA SEALED ------------

  def testSCL_21582_ScalaSealed_VariableDefinition(): Unit = {
    configureScalaSealedWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayScala2ADT = F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala2ADT.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayScala2ADT = Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_ScalaSealedDefinition_Seq(): Unit = {
    configureScalaSealedWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala2ADT] = Seq($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala2ADT.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala2ADT] = Seq(Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_ScalaSealedEnum_Equals(): Unit = {
    configureScalaSealedWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayScala2ADT = ???
           |
           |    enumsInstance == F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala2ADT.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayScala2ADT = ???
          |
          |    enumsInstance == Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_ScalaSealedMethodCall(): Unit = {
    configureScalaSealedWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala2ADT] = ???
           |
           |    enumsSeq.exists(_ == $CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala2ADT.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala2ADT] = ???
          |
          |    enumsSeq.exists(_ == Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_ScalaSealed_MethodCall(): Unit = {
    configureScalaSealedWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala2ADT] = ???
           |
           |    enumsSeq.contains($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala2ADT.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala2ADT] = ???
          |
          |    enumsSeq.contains(Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_ScalaSealed_MethodCall2(): Unit = {
    configureScalaSealedWeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    foo($CARET)
           |  }
           |
           |  def foo(p: WeekDayScala2ADT): Unit = ???
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala2ADT.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    foo(Friday)
          |  }
          |
          |  def foo(p: WeekDayScala2ADT): Unit = ???
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  /// ---------- SCALA SEALED ------------
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
final class ScalaTypeMatchingEnumBasicCompletionTest_2_13 extends ScalaTypeMatchingEnumBasicCompletionTest

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
final class ScalaTypeMatchingEnumBasicCompletionTest_3_Latest extends ScalaTypeMatchingEnumBasicCompletionTest {

  // TODO: ERROR: Tree access disabled in org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.EnumMembersInjector#injectEnumCase
  //       SCL-22912
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

  private def configureScala3WeekdayEnum(): Unit = myFixture.addFileToProject("completion/WeekDayScala3.scala",
    """
      |package completion
      |
      |enum WeekDayScala3 {
      |  case Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday
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

  def testScala3EnumInMethodCall(): Unit = withoutAstLoadingFilter {
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

  def testScala3EnumInMethodCallWithNamedArgument(): Unit = withoutAstLoadingFilter {
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

  def testSCL_21582_Scala3Enum_VariableDefinition(): Unit = {
    configureScala3WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayScala3 = F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala3.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayScala3 = Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala3Enum_VariableDefinition_Seq(): Unit = {
    configureScala3WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala3] = Seq($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala3.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala3] = Seq(Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala3Enum_Equals(): Unit = {
    configureScala3WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsInstance: WeekDayScala3 = ???
           |
           |    enumsInstance == F$CARET
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala3.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsInstance: WeekDayScala3 = ???
          |
          |    enumsInstance == Friday
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala3Enum_Equals_InMethodCall(): Unit = {
    configureScala3WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala3] = ???
           |
           |    enumsSeq.exists(_ == $CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala3.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala3] = ???
          |
          |    enumsSeq.exists(_ == Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala3Enum_MethodCall(): Unit = {
    configureScala3WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    val enumsSeq: Seq[WeekDayScala3] = ???
           |
           |    enumsSeq.contains($CARET)
           |  }
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala3.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    val enumsSeq: Seq[WeekDayScala3] = ???
          |
          |    enumsSeq.contains(Friday)
          |  }
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  def testSCL_21582_Scala3Enum_MethodCall2(): Unit = {
    configureScala3WeekdayEnum()

    doCompletionTest(
      fileText =
        s"""
           |package completion
           |
           |object Test {
           |  def main(args: Array[String]): Unit = {
           |    foo($CARET)
           |  }
           |
           |  def foo(p: WeekDayScala3): Unit = ???
           |}
           |""".stripMargin,
      resultText =
        """
          |package completion
          |
          |import completion.WeekDayScala3.Friday
          |
          |object Test {
          |  def main(args: Array[String]): Unit = {
          |    foo(Friday)
          |  }
          |
          |  def foo(p: WeekDayScala3): Unit = ???
          |}
          |""".stripMargin,
      item = "Friday",
      completionType = BASIC
    )
  }

  /// ---------- SCALA 3 ENUM ------------
}
