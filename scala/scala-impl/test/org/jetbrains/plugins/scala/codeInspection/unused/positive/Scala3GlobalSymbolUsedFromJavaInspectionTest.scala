package org.jetbrains.plugins.scala.codeInspection.unused.positive

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.unused.ScalaUnusedSymbolInspectionTestBase

class Scala3GlobalSymbolUsedFromJavaInspectionTest extends ScalaUnusedSymbolInspectionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  private def addJavaFile(text: String): Unit = myFixture.addFileToProject("Bar.java", text)

  def test_enum_case_accessed_from_java(): Unit = {
    addJavaFile(
      """
        |public class Bar {
        |    public static void main(String[] args) {
        |       var foo = Foo.FOO;
        |       System.out.println(foo.toString());
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |""".stripMargin)
  }

  def test_other_enum_case_accessed_from_java(): Unit = {
    addJavaFile(
      """
        |public class Bar {
        |    public static void main(String[] args) {
        |       var bar = Foo.BAR;
        |       System.out.println(bar.toString());
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |  case BAR
        |""".stripMargin)
  }

  def test_enum_cases_used_if_java_uses_values(): Unit = {
    addJavaFile(
      """
        |public class Bar {
        |    public static void main(String[] args) {
        |       System.out.println(Foo.values());
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |  case BAR
        |""".stripMargin)
  }

  def test_enum_cases_used_if_java_uses_EnumSet_allOf(): Unit = {
    addJavaFile(
      """
        |public class Bar {
        |    public static void main(String[] args) {
        |       var enumSet = EnumSet.allOf(Foo.class);
        |       System.out.println(enumSet)
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |  case BAR
        |""".stripMargin)
  }

  def test_enum_cases_used_if_java_uses_EnumSet_complementOf(): Unit = {
    addJavaFile(
      """
        |public class Bar {
        |    public static void main(String[] args) {
        |       var enumSet = EnumSet.complementOf(EnumSet.of(Foo.FOO));
        |       System.out.println(enumSet)
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |  case BAR
        |""".stripMargin)
  }

  def test_enum_cases_used_if_java_uses_EnumSet_range(): Unit = {
    addJavaFile(
      """
        |public class Bar {
        |    public static void main(String[] args) {
        |       var enumSet = EnumSet.range(Foo.FOO, Foo.BAR);
        |       System.out.println(enumSet)
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |  case BAR
        |""".stripMargin)
  }

  def test_enum_cases_used_if_java_uses_MODULE_values(): Unit = {
    addJavaFile(
      """
        |public class Bar {
        |    public static void main(String[] args) {
        |       System.out.println(Foo$.MODULE$.values());
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |  case BAR
        |""".stripMargin)
  }

  def test_enum_cases_used_if_java_uses_import(): Unit = {
    addJavaFile(
      """
        |import Foo.FOO;
        |
        |public class Bar {
        |    public static void main(String[] args) {
        |       System.out.println(FOO.toString());
        |    }
        |}
        |""".stripMargin)

    checkTextHasNoErrors(
      """
        |enum Foo:
        |  case FOO
        |  case BAR
        |""".stripMargin)
  }
}
