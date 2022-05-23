package org.jetbrains.plugins.scala.conversion.copy

import org.jetbrains.plugins.scala.conversion.copy.plainText.TextJavaCopyPastePostProcessor
import org.jetbrains.plugins.scala.editor.copy.CopyPasteTestBase
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class CopyTextToScalaTest extends CopyPasteTestBase {
  override val fromLangExtension: String = ".txt"

  override protected def setUp(): Unit = {
    super.setUp()
    ScalaProjectSettings.getInstance(getProject).setDontShowConversionDialog(true)
  }

  override protected def doTest(fromText: String, toText: String, expectedText: String): Unit = {
    TextJavaCopyPastePostProcessor.insideIde = false
    super.doTest(fromText, toText, expectedText)
    TextJavaCopyPastePostProcessor.insideIde = true
  }

  def testWrapWithExpression(): Unit = {
    val fromText = s"${Start}new double[]{1.0, 2, 3};$End"

    val expected = "Array[Double](1.0, 2, 3)"

    doTestToEmptyFile(fromText, expected)
  }

  def testWrapWithFunction(): Unit = {
    val fromText =
      s"""
         |${Start}assert true : "Invocation of 'paste' operation for specific caret is not supported";$End
      """.stripMargin

    val expected =
      s"""assert(true, "Invocation of 'paste' operation for specific caret is not supported")""".stripMargin
    doTestToEmptyFile(fromText, expected)
  }

  def testWrapWithClass(): Unit = {
    val fromText =
      s"""
         |${Start}public void doExecute() {
         |   assert true : "Invocation of 'paste' operation for specific caret is not supported";
         |}$End
      """.stripMargin

    val expected =
      """def doExecute(): Unit = {
        |  assert(true, "Invocation of 'paste' operation for specific caret is not supported")
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testWrapWithClass2(): Unit = {
    val fromText =
      s"""
         |${Start}int i = 6;
         |boolean a = false;
         |String s = "false";$End
      """.stripMargin

    val expected =
      """val i: Int = 6
        |val a: Boolean = false
        |val s: String = "false"""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testAsFile(): Unit = {
    val fromText =
      s"""
         |${Start}import java.io.File;
         |public class Main {
         |	int func() {
         |		int qwe = 34;
         |		Boolean b = true;
         |		File f = new File("sdf");
         |		return 21;
         |	}
         |}$End
      """.stripMargin

    val expected =
      """import java.io.File
        |
        |class Main {
        |  def func: Int = {
        |    val qwe: Int = 34
        |    val b: Boolean = true
        |    val f: File = new File("sdf")
        |    21
        |  }
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testJavaFileWithoutSemicolon(): Unit = {
    val fromText =
      s"""
         |${Start}class Test {
         |    public static void main(String[] args) {
         |        System.out.println("hello")
         |        System.out.println(" how are you?");
         |    }
         |}$End
      """.stripMargin

    val expected =
      """object Test {
        |  def main(args: Array[String]): Unit = {
        |    System.out.println("hello")
        |    System.out.println(" how are you?")
        |  }
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testPrefixedExpression(): Unit = {
    val fromText = s"""${Start}Arrays.asList("peter", "anna", "mike", "xenia");$End""".stripMargin
    val expected =
      """import java.util
        |
        |util.Arrays.asList("peter", "anna", "mike", "xenia")""".stripMargin
    doTestToEmptyFile(fromText, expected)
  }

  def testJavaMethodWithoutLatestCurlyBrackets(): Unit = {
    val fromText =
      s"""
         |class Test {
         |    ${Start}public static void main(String[] args) {
         |        System.out.println("hello");
         |        System.out.println(" how are you?"); $End
         |}
      """.stripMargin


    val expected =
      """def main(args: Array[String]): Unit = {
        |  System.out.println("hello")
        |  System.out.println(" how are you?")
        |}""".stripMargin
    doTestToEmptyFile(fromText, expected)
  }

  def testEmptyJavaClass(): Unit = {
    doTestToEmptyFile(s"${Start}public class Test {}$End", "class Test {}")
  }

  def testSCL11425(): Unit = {
    val fromText =
      s"""
         |${Start}public class Salary extends Employee {
         |   private double salary;
         |}$End
         |""".stripMargin

    val toText =
      s"""
         |abstract class Employee {
         |   private String name;
         |}
         |
         |$Caret""".stripMargin

    val expected =
      """
        |abstract class Employee {
        |   private String name;
        |}
        |
        |class Salary extends Employee {
        |  private val salary: Double = .0
        |}""".stripMargin

    doTest(fromText, toText, expected)
  }

  /** ****************** Valid scala code. No conversion expected. ******************/

  def testNoConversion1(): Unit = {
    doTestToEmptyFile(s"${Start}class Test {}$End", "class Test {}")
  }

  def testNoConversion2(): Unit = {
    doTestToEmptyFile(s"${Start}class Test extends Any$End", "class Test extends Any")
  }


  def testPasteToString(): Unit = {
    val fromText =
      s"""public class Test {
         |   ${Start}public static int number = 42;$End
         |
         |   public static int number2 = Test.number;
         |}
         |""".stripMargin

    val toText =
      s"""
         |abstract class ScalaClass {
         |  "$Caret"
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  "public static int number = 42;"
         |}""".stripMargin

    doTest(fromText, toText, expected)
  }

  def testPasteToMultilineString(): Unit = {
    val fromText =
      s"""public class Test {
         |   ${Start}public static int number = 42;
         |$End
         |   public static int number2 = Test.number;
         |}
         |""".stripMargin

    val qqq = "\"\"\""

    val toText =
      s"""
         |abstract class ScalaClass {
         |  $qqq$Caret$qqq
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  ${qqq}public static int number = 42;
         |    |$qqq.stripMargin
         |}""".stripMargin

    doTest(fromText, toText, expected)
  }


}
