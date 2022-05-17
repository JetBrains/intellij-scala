package org.jetbrains.plugins.scala
package conversion
package copy

import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaProjectSettings}

/**
 * Check imports addition to converted code, when they were not copied
 *
 * Conversion functionality from java to scala is tested in
 * [[org.jetbrains.plugins.scala.conversion.JavaToScalaConversionTestBase]]
 */
class CopyJavaToScalaTest extends CopyPasteTestBase {
  override val fromLangExtension: String = ".java"

  override def setUp(): Unit = {
    super.setUp()
    ScalaProjectSettings.getInstance(getProject).setDontShowConversionDialog(true)
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES
  }

  def testAddSimpleImport(): Unit = {
    val fromText =
      s"""
         |import java.io.File;
         |
         |${Start}public class AnonymousClass {
         |   File file = new File("super");
         |}$End
      """.stripMargin

    val expected =
      """import java.io.File
        |
        |class AnonymousClass {
        |  val file: File = new File("super")
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testRefAsArray(): Unit = {
    val fromText =
      s"""
         |import java.io.File;
         |
         |${Start}public class Test {
         |   File[] array = new File[23];
         |}$End
      """.stripMargin

    val expected =
      """import java.io.File
        |
        |class Test {
        |  val array: Array[File] = new Array[File](23)
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testParametrizedType(): Unit = {
    val fromText =
      s"""
         |import java.util.ArrayList;
         |import java.util.List;
         |
         |${Start}public class Test {
         |    List<Integer> list = new ArrayList<Integer>();
         |}$End
      """.stripMargin

    val expected =
      """import java.util
        |import java.util.{ArrayList, List}
        |
        |class Test {
        |  val list: util.List[Integer] = new util.ArrayList[Integer]
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testPackageWithComment(): Unit = {
    val fromText =
      s"""$Start//comment before
         |package qwert;
         |
         |import java.util.ArrayList;
         |import java.util.HashSet;
         |import java.util.List;
         |import java.util.Set;
         |
         |
         |public class Test {
         |    List<Integer> list = new ArrayList<Integer>();
         |
         |    private static class Inner {
         |        void foo() {
         |            Set<String> st = new HashSet<>();
         |        }
         |    }
         |}$End""".stripMargin

    val expected =
      """//comment before
        |package qwert
        |
        |import java.util
        |import java.util.{ArrayList, HashSet, List, Set}
        |
        |
        |object Test {
        |  private class Inner {
        |    private[qwert] def foo(): Unit = {
        |      val st: util.Set[String] = new util.HashSet[String]
        |    }
        |  }
        |}
        |
        |class Test {
        |  private[qwert] val list: util.List[Integer] = new util.ArrayList[Integer]
        |}""".stripMargin

    doTestToEmptyFile(fromText, expected)
  }

  def testSCL15869(): Unit = {
    val fromText =
      s"""public class Test {
         |   public static int number = 42;
         |
         |   public static int number2 = ${Start}Test.number$End;
         |}
         |""".stripMargin

    val toText =
      s"""
         |abstract class ScalaClass {
         |   if (true) {
         |   $Caret
         |   } else {
         |
         |   }
         |}""".stripMargin

    val expected =
      s"""
         |abstract class ScalaClass {
         |   if (true) {
         |     Test.number
         |   } else {
         |
         |   }
         |}""".stripMargin

    doTest(fromText, toText, expected)
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
