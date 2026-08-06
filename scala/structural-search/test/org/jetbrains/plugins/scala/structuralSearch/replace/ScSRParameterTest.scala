package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.structuralsearch.MatchOptions
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralReplaceTestCase

class ScSRParameterTest extends ScalaStructuralReplaceTestCase {

  def testNoChange(): Unit = {
    val content =
      """def func01(): Unit = println("Hello World!")
        |def func02(a): Int = println("World! Hello")
        |def func03(a, b): Int = println("Hello! World")
        |def func04(a: Int): Unit = println(a)
        |def func05(a: String, b): Int = println(a + b)
        |def func06(a: String, b: String): Unit = println(":(")
        |def func07(@Deprecated a: String, b: Int): Unit = println("My deprecated text")
        |def func08(a: String, b: Int, c: MyClass, d: TestClass): Unit = println("Test")
        |def func09(a: String, b: Int = 42): Unit = println(b)
        |def func10(a: String = "Hello world!", b = 42): Int = println(a)
        |def func11(@Deprecated a: MyClass = MyClass(), @Nullable b: Int = 2): Unit = println("Hello World!")
        |"""

    content.stripMargin.lines().filter(_.nonEmpty).forEach(line => {
      replaceAndAssert(
        s"No content change no variable, line: $line",
        content, line, line, content
      )
    })

    val patterns = List(
      "def $func$($par$): $ret$ = $body$",
      "def $func$($par$: $ty$): $ret$ = $body$",
      "def $func$($par$: $ty$ = $default$): $ret$ = $body$",
      "def $func$(@$anno$ $par$: $ty$): $ret$ = $body$",
      "def $func$(@$anno$ $par$ = $default$): $ret$ = $body$",
      "def $func$(@$anno$ $par$: $ty$ = $default$): $ret$ = $body$",
    )
    for (pattern <- patterns) {
      replaceAndAssert(
        s"No content change with variables for pattern <$pattern>",
        content,
        pattern,
        pattern.replace("$ret$", "String"),
        (if pattern.contains("$ty$") then content else content.replace("Int = pr", "String = pr")).replace("Unit", "String"),
        mO => {
          constrCount(mO, "par")
          constrCount(mO, "body")
          if (pattern.contains("$anno$")) constrCount(mO, "anno")
          if (pattern.contains("$default$")) constrCount(mO, "default", 0, 1)
        }
      )
    }
  }

  def testNoChangeVariables(): Unit = {
    val content =
      """def func01(): Unit = println("Hello World!")
        |def func02(a): Int = println("World! Hello")
        |def func03(a, b): Int = println("Hello! World")
        |def func04(a: Int): Unit = println(a)
        |def func05(a: String, b): Int = println(a + b)
        |def func06(a: String, b: String): Unit = println(":(")
        |def func07(@Deprecated a: String, b: Int): Unit = println("My deprecated text")
        |def func08(a: String, b: Int, c: MyClass, d: TestClass): Unit = println("Test")
        |def func09(a: String, b: Int = 42): Unit = println(b)
        |def func10(a: String = "Hello world!", b = 42): Int = println(a)
        |def func11(@Deprecated a: MyClass = MyClass(), @Nullable b: Int = 2): Unit = println("Hello World!")
        |""".stripMargin
    val expected =
      """def func01(): Unit = println("Hello World!")
        |def func02(a): Int = println("World! Hello")
        |def func03(a, b): Int = println("Hello! World")
        |def func04(a: Int): Unit = println(a)
        |def func05(a: String, b): Int = println(a + b)
        |def func06(a: String, b: String): Unit = println(":(")
        |def func07(@Deprecated a: String, b: Int): Unit = println("My deprecated text")
        |def func08(a: String, b: Int, c: MyClass, d: TestClass): Unit = println("Test")
        |def func09(a: String, b: Int = 42): Unit = println(b)
        |def func10(a: String = "Hello world!", b = 42): Int = println(a)
        |def func11(@Deprecated a: MyClass = MyClass(), @Nullable b: Int = 2): Unit = println("Hello World!")
        |""".stripMargin
    val patterns = List(
      ("def $func$($par$): $ret$ = $body$", "def $func$($par$): $ret$ = $body$"),
      ("def $func$($par$: $ty$): $ret$ = $body$", "def $func$($par$: $ty$): $ret$ = $body$"),
      ("def $func$($par$: $ty$ = $default$): $ret$ = $body$", "def $func$($par$: $ty$ = $default$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$): $ret$ = $body$", "def $func$(@$anno$ $par$: $ty$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$ = $default$): $ret$ = $body$", "def $func$(@$anno$ $par$ = $default$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$ = $default$): $ret$ = $body$", "def $func$(@$anno$ $par$: $ty$ = $default$): $ret$ = $body$"),
    )
    for ((spattern, rpattern) <- patterns) {
      val (cont, exp) = if (spattern.contains("$ty$")) {
        val (cont, exp) = content.split("\n").zip(expected.split("\n")).filter((cont, _) => cont.contains("Unit = pr")).unzip
        (cont.mkString("\n"), exp.mkString("\n"))
      } else {
        (content, expected)
      }
      replaceAndAssert(
        s"No content change with variables for pattern <$spattern>",
        cont,
        spattern,
        rpattern,
        exp,
        mO => {
          constrCount(mO, "par")
          constrCount(mO, "body")
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$default$")) constrCount(mO, "default", 0, 1)
        }
      )
    }
  }

  def testChangeParType(): Unit = {
    val content =
      """def func01(): Unit = println("Hello World!")
        |def func02(a): Int = println("World! Hello")
        |def func03(a, b): Int = println("Hello! World")
        |def func04(a: Int): Unit = println(a)
        |def func05(a: String, b): Int = println(a + b)
        |def func06(a: String, b: String): Unit = println(":(")
        |def func07(@Deprecated a: String, b: Int): Unit = println("My deprecated text")
        |def func08(a: String, b: Int, c: MyClass, d: TestClass): Unit = println("Test")
        |def func09(a: String, b: Int = 42): Unit = println(b)
        |def func10(a: String = "Hello world!", b = 42): Int = println(a)
        |def func11(@Deprecated a: MyClass = MyClass(), @Nullable b: Int = 2): Unit = println("Hello World!")
        |""".stripMargin
    val expected =
      """def func01(): Unit = println("Hello World!")
        |def func02(a: String): Int = println("World! Hello")
        |def func03(a: String, b: String): Int = println("Hello! World")
        |def func04(a: String): Unit = println(a)
        |def func05(a: String, b: String): Int = println(a + b)
        |def func06(a: String, b: String): Unit = println(":(")
        |def func07(@Deprecated a: String, b: String): Unit = println("My deprecated text")
        |def func08(a: String, b: String, c: String, d: String): Unit = println("Test")
        |def func09(a: String, b: String = 42): Unit = println(b)
        |def func10(a: String = "Hello world!", b: String = 42): Int = println(a)
        |def func11(@Deprecated a: String = MyClass(), @Nullable b: String = 2): Unit = println("Hello World!")
        |""".stripMargin
    val patterns = List(
      ("def $func$($par$): $ret$ = $body$", "def $func$($par$: String): $ret$ = $body$"),
      ("def $func$($par$: $ty$): $ret$ = $body$", "def $func$($par$: String): $ret$ = $body$"),
      ("def $func$($par$: $ty$ = $default$): $ret$ = $body$", "def $func$($par$: String = $default$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$): $ret$ = $body$", "def $func$(@$anno$ $par$: String): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$ = $default$): $ret$ = $body$", "def $func$(@$anno$ $par$: String = $default$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$ = $default$): $ret$ = $body$", "def $func$(@$anno$ $par$: String = $default$): $ret$ = $body$"),
    )
    for ((spattern, rpattern) <- patterns) {
      val (cont, exp) = if (spattern.contains("$ty$")) {
        val (cont, exp) = content.split("\n").zip(expected.split("\n")).filter((cont, _) => cont.contains("Unit = pr")).unzip
        (cont.mkString("\n"), exp.mkString("\n"))
      } else {
        (content, expected)
      }
      replaceAndAssert(
        s"Change type for pattern <$spattern>",
        cont,
        spattern,
        rpattern,
        exp,
        mO => {
          constrCount(mO, "par")
          constrCount(mO, "body")
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$default$")) constrCount(mO, "default", 0, 1)
        }
      )
    }
  }

  def testChangeParDefault(): Unit = {
    val content =
      """def func01(): Unit = println("Hello World!")
        |def func02(a): Int = println("World! Hello")
        |def func03(a, b): Int = println("Hello! World")
        |def func04(a: Int): Unit = println(a)
        |def func05(a: String, b): Int = println(a + b)
        |def func06(a: String, b: String): Unit = println(":(")
        |def func07(@Deprecated a: String, b: Int): Unit = println("My deprecated text")
        |def func08(a: String, b: Int, c: MyClass, d: TestClass): Unit = println("Test")
        |def func09(a: String, b: Int = 42): Unit = println(b)
        |def func10(a: String = "Hello world!", b = 42): Int = println(a)
        |def func11(@Deprecated a: MyClass = MyClass(), @Nullable b: Int = 2): Unit = println("Hello World!")
        |""".stripMargin
    val expected =
      """def func01(): Unit = println("Hello World!")
        |def func02(a = fourtytwo): Int = println("World! Hello")
        |def func03(a = fourtytwo, b = fourtytwo): Int = println("Hello! World")
        |def func04(a: Int = fourtytwo): Unit = println(a)
        |def func05(a: String = fourtytwo, b = fourtytwo): Int = println(a + b)
        |def func06(a: String = fourtytwo, b: String = fourtytwo): Unit = println(":(")
        |def func07(@Deprecated a: String = fourtytwo, b: Int = fourtytwo): Unit = println("My deprecated text")
        |def func08(a: String = fourtytwo, b: Int = fourtytwo, c: MyClass = fourtytwo, d: TestClass = fourtytwo): Unit = println("Test")
        |def func09(a: String = fourtytwo, b: Int = fourtytwo): Unit = println(b)
        |def func10(a: String = fourtytwo, b = fourtytwo): Int = println(a)
        |def func11(@Deprecated a: MyClass = fourtytwo, @Nullable b: Int = fourtytwo): Unit = println("Hello World!")
        |""".stripMargin
    val patterns = List(
      ("def $func$($par$): $ret$ = $body$", "def $func$($par$ = fourtytwo): $ret$ = $body$"),
      ("def $func$($par$: $ty$): $ret$ = $body$", "def $func$($par$: $ty$ = fourtytwo): $ret$ = $body$"),
      ("def $func$($par$: $ty$ = $default$): $ret$ = $body$", "def $func$($par$: $ty$ = fourtytwo): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$): $ret$ = $body$", "def $func$(@$anno$ $par$: $ty$ = fourtytwo): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$ = $default$): $ret$ = $body$", "def $func$(@$anno$ $par$ = fourtytwo): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$ = $default$): $ret$ = $body$", "def $func$(@$anno$ $par$: $ty$ = fourtytwo): $ret$ = $body$"),
    )
    for ((spattern, rpattern) <- patterns) {
      val (cont, exp) = if (spattern.contains("$ty$")) {
        val (cont, exp) = content.split("\n").zip(expected.split("\n")).filter((cont, _) => cont.contains("Unit = pr")).unzip
        (cont.mkString("\n"), exp.mkString("\n"))
      } else {
        (content, expected)
      }
      replaceAndAssert(
        s"Change default for pattern <$spattern>",
        cont,
        spattern,
        rpattern,
        exp,
        mO => {
          constrCount(mO, "par")
          constrCount(mO, "body")
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$default$")) constrCount(mO, "default", 0, 1)
        }
      )
    }
  }

  def testChangeParAnnotations(): Unit = {
    val content =
      """def func01(): Unit = println("Hello World!")
        |def func02(a): Int = println("World! Hello")
        |def func03(a, b): Int = println("Hello! World")
        |def func04(a: Int): Unit = println(a)
        |def func05(a: String, b): Int = println(a + b)
        |def func06(a: String, b: String): Unit = println(":(")
        |def func07(@Deprecated a: String, b: Int): Unit = println("My deprecated text")
        |def func08(a: String, b: Int, c: MyClass, d: TestClass): Unit = println("Test")
        |def func09(a: String, b: Int = 42): Unit = println(b)
        |def func10(a: String = "Hello world!", b = 42): Int = println(a)
        |def func11(@Deprecated a: MyClass = MyClass(), @Nullable b: Int = 2): Unit = println("Hello World!")
        |""".stripMargin
    val expected =
      """def func01(): Unit = println("Hello World!")
        |def func02(@anno a): Int = println("World! Hello")
        |def func03(@anno a, @anno b): Int = println("Hello! World")
        |def func04(@anno a: Int): Unit = println(a)
        |def func05(@anno a: String, @anno b): Int = println(a + b)
        |def func06(@anno a: String, @anno b: String): Unit = println(":(")
        |def func07(@anno a: String, @anno b: Int): Unit = println("My deprecated text")
        |def func08(@anno a: String, @anno b: Int, @anno c: MyClass, @anno d: TestClass): Unit = println("Test")
        |def func09(@anno a: String, @anno b: Int = 42): Unit = println(b)
        |def func10(@anno a: String = "Hello world!", @anno b = 42): Int = println(a)
        |def func11(@anno a: MyClass = MyClass(), @anno b: Int = 2): Unit = println("Hello World!")
        |""".stripMargin
    val patterns = List(
      ("def $func$($par$): $ret$ = $body$", "def $func$(@anno $par$): $ret$ = $body$"),
      ("def $func$($par$: $ty$): $ret$ = $body$", "def $func$(@anno $par$: $ty$): $ret$ = $body$"),
      ("def $func$($par$: $ty$ = $default$): $ret$ = $body$", "def $func$(@anno $par$: $ty$ = $default$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$): $ret$ = $body$", "def $func$(@anno $par$: $ty$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$ = $default$): $ret$ = $body$", "def $func$(@anno $par$ = $default$): $ret$ = $body$"),
      ("def $func$(@$anno$ $par$: $ty$ = $default$): $ret$ = $body$", "def $func$(@anno $par$: $ty$ = $default$): $ret$ = $body$"),
    )
    for ((spattern, rpattern) <- patterns) {
      val (cont, exp) = if (spattern.contains("$ty$")) {
        val (cont, exp) = content.split("\n").zip(expected.split("\n")).filter((cont, _) => cont.contains("Unit = pr")).unzip
        (cont.mkString("\n"), exp.mkString("\n"))
      } else {
        (content, expected)
      }
      replaceAndAssert(
        s"No content change with variables for pattern <$spattern>",
        cont,
        spattern,
        rpattern,
        exp,
        mO => {
          constrCount(mO, "par")
          constrCount(mO, "body")
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$default$")) constrCount(mO, "default", 0, 1)
        }
      )
    }
  }

  private def constrCount(matchOptions: MatchOptions, name: String, min: Int = 0, max: Int = 100): Unit = {
    val constrBody = matchOptions.addNewVariableConstraint(name)
    constrBody.setMinCount(min)
    constrBody.setMaxCount(max)
  }
}
