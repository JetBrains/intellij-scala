package org.jetbrains.plugins.scala.lang.overrideImplement

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

class ImplementMembersQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  //noinspection NotImplementedCode
  override protected def description: String = "...must either be declared abstract or implement abstract member..."

  override protected def descriptionMatches(s: String): Boolean =
    s != null && {
      s.contains("must either be declared abstract or implement abstract member") ||
        s.startsWith("Object creation is impossible since member") && s.endsWith("is not defined")
    }

  def testProperDefaultValueForVariousMemberTypes(): Unit = {
    val before =
      s"""abstract class MyTrait {
         |  var myVariable: Int
         |  val myValue: Int
         |  def myDef: Int
         |  type MyType
         |}
         |
         |${START}class$CARET MyClass1 extends MyTrait$END
         |""".stripMargin

    val after =
      s"""abstract class MyTrait {
         |  var myVariable: Int
         |  val myValue: Int
         |  def myDef: Int
         |  type MyType
         |}
         |
         |class MyClass1 extends MyTrait {
         |  override var myVariable: Int = ${START}???$END
         |  override val myValue: Int = ???
         |
         |  override def myDef: Int = ???
         |
         |  override type MyType = this.type
         |}
         |""".stripMargin

    checkTextHasError(before)
    testQuickFix(before, after, "Implement members")
  }

  def testCaretInTheBeginning(): Unit = {
    val before =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |$CARET${START}class MyClass extends MyTrait$END
         |""".stripMargin

    val after =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |class MyClass extends MyTrait {
         |  override def myDef: Int = $START???$END
         |}
         |""".stripMargin

    checkTextHasError(before)
    testQuickFix(before, after, "Implement members")
  }

  def testCaretInTheEnd(): Unit = {
    val before =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |${START}class MyClass extends MyTrait$END$CARET
         |
         |class Other
         |""".stripMargin

    val after =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |class MyClass extends MyTrait {
         |  override def myDef: Int = $START???$END
         |}
         |
         |class Other
         |""".stripMargin

    checkTextHasError(before)
    testQuickFix(before, after, "Implement members")
  }

  def testCaretInTheEnd_EOF(): Unit = {
    val before =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |${START}class MyClass extends MyTrait$END$CARET
         |""".stripMargin.trim

    val after =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |class MyClass extends MyTrait {
         |  override def myDef: Int = $START???$END
         |}
         |""".stripMargin.trim

    checkTextHasError(before)
    testQuickFix(before, after, "Implement members")
  }

  //SCL-665
  def testCaretInTheEndOfNewTemplateDefinition(): Unit = {
    val before =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |object Wrapper {
         |  val x = new ${START}MyTrait$END$CARET
         |}
         |""".stripMargin

    val after =
      s"""abstract class MyTrait {
         |  def myDef: Int
         |}
         |object Wrapper {
         |  val x = new MyTrait {
         |    override def myDef: Int = $START???$END
         |  }
         |}""".stripMargin

    checkTextHasError(before)
    testQuickFix(before, after, "Implement members")
  }
}