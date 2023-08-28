package org.jetbrains.plugins.scala.conversion.copy

import org.jetbrains.plugins.scala.conversion.copy.plainText.TextJavaCopyPastePostProcessor
import org.jetbrains.plugins.scala.lang.actions.editor.copy.CopyPasteTestBase
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class CopyTextToScalaTest extends CopyPasteTestBase {
  override val fromLangExtension: String = "txt"

  override protected def setUp(): Unit = {
    super.setUp()
    ScalaProjectSettings.getInstance(getProject).setDontShowConversionDialog(true)
  }

  override protected def doTest(from: String, to: String, after: String, fromFileName: String, toFileName: String): Unit = {
    val insideIdeBefore = TextJavaCopyPastePostProcessor.insideIde
    TextJavaCopyPastePostProcessor.insideIde = false
    try {
      super.doTest(from: String, to: String, after: String, fromFileName: String, toFileName: String)
    } finally {
      TextJavaCopyPastePostProcessor.insideIde = insideIdeBefore
    }
  }

  def testExpression_ArrayAllocation(): Unit = {
    val fromText =
      s"""class MyClass {
         |  ${Start}new double[]{1.0, 2, 3};$End
         |}
         |""".stripMargin

    val expected = "Array[Double](1.0, 2, 3)"

    doTestToEmptyFile(fromText, expected)
  }

  def testExpression_ArrayAllocation_TopLevel(): Unit = {
    val fromText = s"${Start}new double[]{1.0, 2, 3};$End"

    val expected = "Array[Double](1.0, 2, 3)"

    doTestToEmptyFile(fromText, expected)
  }

  def testExpression_Assert(): Unit = {
    val fromText =
      s"""
         |${Start}assert true : "Invocation of 'paste' operation for specific caret is not supported";$End
      """.stripMargin

    val expected =
      s"""assert(true, "Invocation of 'paste' operation for specific caret is not supported")""".stripMargin
    doTestToEmptyFile(fromText, expected)
  }

  def testFunction_TopLevel(): Unit = {
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

  def testFunction_ClassMember(): Unit = {
    val fromText =
      s"""public class MyClass {
         |  ${Start}public void doExecute() {
         |     assert true : "Invocation of 'paste' operation for specific caret is not supported";
         |  }$End
         |}
      """.stripMargin

    val expected =
      """def doExecute(): Unit = {
        |  assert(true, "Invocation of 'paste' operation for specific caret is not supported")
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }


  def testFunction_NonPublicClassMember(): Unit = {
    val fromText =
      s"""class MyClass {
         |  ${Start}public void doExecute() {
         |     assert true : "Invocation of 'paste' operation for specific caret is not supported";
         |  }$End
         |}
      """.stripMargin

    val expected =
      """def doExecute(): Unit = {
        |  assert(true, "Invocation of 'paste' operation for specific caret is not supported")
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testMultipleMembers_TopLevel(): Unit = {
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

  def testMultipleMembers_TopLevel_2(): Unit = {
    val fromText =
      s"""${Start}int i = 6;
         |public void doExecute1() {
         |    assert true : "message";
         |}
         |boolean a = false;
         |public void doExecute2() {
         |    assert true : "message";
         |}
         |String s = "false";$End
      """.stripMargin

    val expected =
      """val i: Int = 6
        |def doExecute1(): Unit = {
        |  assert(true, "message")
        |}
        |val a: Boolean = false
        |def doExecute2(): Unit = {
        |  assert(true, "message")
        |}
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

  def testCopyFromTextToScalaShouldNotTryGuessingWhichImportsToAdd(): Unit = {
    val fromText =
      s"""${Start}List<String> names = Arrays.asList("peter", "anna", "mike", "xenia");
        |
        |Collections.sort(names, new Comparator<String>() {
        |    @Override
        |    public int compare(String a, String b) {
        |        return b.compareTo(a);
        |    }
        |});$End""".stripMargin

    val toText =
      s"""class A {
         |  $Caret
         |}
         |""".stripMargin

    val expected =
      """class A {
        |  val names: Nothing = Arrays.asList("peter", "anna", "mike", "xenia")
        |
        |  Collections.sort(names, new Nothing() {
        |    def compare(a: String, b: String): Int = b.compareTo(a)
        |  })
        |}
        |""".stripMargin

    doTest(fromText, toText, expected)
  }

  /** ****************** Valid scala code. No conversion expected. ******************/

  def testNoConversion1(): Unit = {
    doTestToEmptyFile(s"${Start}class Test {}$End", "class Test {}")
  }

  def testNoConversion2(): Unit = {
    doTestToEmptyFile(s"${Start}class Test extends Any$End", "class Test extends Any")
  }


  /** ****************** Paste to string literals shouldn't invoke conversion ***************** */


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

  def testPasteToString_WithExistingText(): Unit = {
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
         |  "${Caret}existing text"
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  "public static int number = 42;existing text"
         |}""".stripMargin

    doTest(fromText, toText, expected)
  }

  def testPasteToInterpolatedString(): Unit = {
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
         |  s"$Caret"
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  s"public static int number = 42;"
         |}""".stripMargin

    doTest(fromText, toText, expected)
  }

  def testPasteToInterpolatedString_WithSomeText(): Unit = {
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
         |  s"${Caret}existing text"
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  s"public static int number = 42;existing text"
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

  def testPasteToMultilineString_WithExistingText(): Unit = {
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
         |  $qqq${Caret}existing text$qqq
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  ${qqq}public static int number = 42;
         |    |existing text$qqq.stripMargin
         |}""".stripMargin

    doTest(fromText, toText, expected)
  }

  def testPasteToMultilineInterpolatedString(): Unit = {
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
         |  s$qqq$Caret$qqq
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  s${qqq}public static int number = 42;
         |     |$qqq.stripMargin
         |}""".stripMargin

    doTest(fromText, toText, expected)
  }

  def testPasteToMultilineInterpolatedString_WithExistingText(): Unit = {
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
         |  s$qqq${Caret}existing text$qqq
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |  s${qqq}public static int number = 42;
         |     |existing text$qqq.stripMargin
         |}""".stripMargin

    doTest(fromText, toText, expected)
  }
}
