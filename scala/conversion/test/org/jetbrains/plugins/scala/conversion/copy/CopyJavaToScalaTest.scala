package org.jetbrains.plugins.scala
package conversion
package copy

import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.lang.actions.editor.copy.CopyPasteTestBase
import org.jetbrains.plugins.scala.settings.{ScalaApplicationSettings, ScalaProjectSettings}

/**
 * Check imports addition to converted code, when they were not copied
 *
 * Conversion functionality from java to scala is tested in
 * [[org.jetbrains.plugins.scala.conversion.JavaToScalaConversionTestBase]]
 */
class CopyJavaToScalaTest extends CopyPasteTestBase {
  override val fromLangExtension: String = "java"

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

  def testPasteJavaCodeWithNonExistingClass(): Unit = {
    doTest(
      s"""package org.example;
         |
         |$Start
         |abstract class MyClass extends MyNonExistingClass implements java.util.function.Function<MyNonExistingClass, Integer> {
         |    private final MyNonExistingClass field = new MyNonExistingClass();
         |
         |    public void foo(MyNonExistingClass param1, java.util.Optional<MyNonExistingClass> param2, MyNonExistingClass2<MyNonExistingClass> parmam3) {
         |        MyNonExistingClass value = new MyNonExistingClass(42);
         |        value.bar();
         |        java.util.Optional<MyNonExistingClass> optional = java.util.Optional.of(value);
         |    }
         |
         |    public MyNonExistingClass createInstance() {
         |        return new MyNonExistingClass();
         |    }
         |
         |    public java.util.List<MyNonExistingClass> getList() {
         |        return java.util.Arrays.asList(new MyNonExistingClass());
         |    }
         |
         |    public void processArray(MyNonExistingClass[] array) {
         |        for (MyNonExistingClass item : array) {
         |            item.bar();
         |        }
         |    }
         |}
         |$End
         |""".stripMargin,
      "",
      """import java.util
        |import java.util.{List, Optional}
        |import java.util.function.Function
        |
        |abstract class MyClass extends MyNonExistingClass with Function[MyNonExistingClass, Integer] {
        |  final private val field: MyNonExistingClass = new MyNonExistingClass
        |
        |  def foo(param1: MyNonExistingClass, param2: Optional[MyNonExistingClass], parmam3: MyNonExistingClass2[MyNonExistingClass]): Unit = {
        |    val value: MyNonExistingClass = new MyNonExistingClass(42)
        |    value.bar
        |    val optional: Optional[MyNonExistingClass] = java.util.Optional.of(value)
        |  }
        |
        |  def createInstance: MyNonExistingClass = new MyNonExistingClass
        |
        |  def getList: util.List[MyNonExistingClass] = java.util.Arrays.asList(new MyNonExistingClass)
        |
        |  def processArray(array: Array[MyNonExistingClass]): Unit = {
        |    for (item <- array) {
        |      item.bar
        |    }
        |  }
        |}
        |""".stripMargin
    )
  }

  def testPasteJavaCodeWithNonAccessibleClass(): Unit = {
    doTest(
      s"""package org.example;
         |
         |$Start
         |abstract class MyClass extends MyNonExistingClass implements java.util.function.Function<MyNonExistingClass, Integer> {
         |    private final MyNonExistingClass field = new MyNonExistingClass();
         |
         |    public void foo(MyNonExistingClass param1, java.util.Optional<MyNonExistingClass> param2, MyNonExistingClass2<MyNonExistingClass> parmam3) {
         |        MyNonExistingClass value = new MyNonExistingClass(42);
         |        value.bar();
         |        java.util.Optional<MyNonExistingClass> optional = java.util.Optional.of(value);
         |    }
         |
         |    public MyNonExistingClass createInstance() {
         |        return new MyNonExistingClass();
         |    }
         |
         |    public java.util.List<MyNonExistingClass> getList() {
         |        return java.util.Arrays.asList(new MyNonExistingClass());
         |    }
         |
         |    public void processArray(MyNonExistingClass[] array) {
         |        for (MyNonExistingClass item : array) {
         |            item.bar();
         |        }
         |    }
         |}
         |$End
         |
         |class MyNonExistingClass {
         |    public MyNonExistingClass() {}
         |    public MyNonExistingClass(int i) {}
         |    public void bar() {}
         |}
         |
         |class MyNonExistingClass2<T extends MyNonExistingClass> {}
         |""".stripMargin,
      "",
      """import java.util
        |import java.util.{List, Optional}
        |import java.util.function.Function
        |
        |abstract class MyClass extends MyNonExistingClass with Function[MyNonExistingClass, Integer] {
        |  final private val field: MyNonExistingClass = new MyNonExistingClass
        |
        |  def foo(param1: MyNonExistingClass, param2: Optional[MyNonExistingClass], parmam3: MyNonExistingClass2[MyNonExistingClass]): Unit = {
        |    val value: MyNonExistingClass = new MyNonExistingClass(42)
        |    value.bar()
        |    val optional: Optional[MyNonExistingClass] = java.util.Optional.of(value)
        |  }
        |
        |  def createInstance: MyNonExistingClass = new MyNonExistingClass
        |
        |  def getList: util.List[MyNonExistingClass] = java.util.Arrays.asList(new MyNonExistingClass)
        |
        |  def processArray(array: Array[MyNonExistingClass]): Unit = {
        |    for (item <- array) {
        |      item.bar()
        |    }
        |  }
        |}
        |""".stripMargin
    )
  }
}

class CopyJavaToScalaTest_Scala3 extends CopyJavaToScalaTest {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testImportedAddedAfterJavaConversion(): Unit = doTest(
    s"""
      |import java.net.URI;
      |import java.net.IDN;
      |
      |${START}public class JavaTest {
      |    void test(URI uri, IDN idn) {}
      |}$END
      |
      |""".stripMargin,
    s"""
      |object Outer:
      |  object Inner:
      |    $CARET
      |""".stripMargin,
    """import java.net.{IDN, URI}
      |
      |object Outer:
      |  object Inner:
      |    class JavaTest {
      |      def test(uri: URI, idn: IDN): Unit = {
      |      }
      |    }
      |""".stripMargin,
  )
}