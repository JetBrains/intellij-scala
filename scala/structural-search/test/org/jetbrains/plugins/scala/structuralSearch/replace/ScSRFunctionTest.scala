package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.structuralsearch.MatchOptions
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralReplaceTestCase

class ScSRFunctionTest extends ScalaStructuralReplaceTestCase {

  val content = Seq(
    """def func01()""",
    """def func02(): Unit""",
    """def func03() = println("Hello world!")""",
    """def func04(): Unit = println("Hello world!")""",
    """def func05[T]()""",
    """def func06[T](): Unit""",
    """def func07[T]() = println("Hello world!")""",
    """def func08[T](): Unit = println("Hello world!")""",
    """protected def func09()""",
    """protected def func10(): Unit""",
    """protected def func11() = println("Hello world!")""",
    """protected def func12(): Unit = println("Hello world!")""",
    """protected def func13[T]()""",
    """protected def func14[T](): Unit""",
    """protected def func15[T]() = println("Hello world!")""",
    """protected def func16[T](): Unit = println("Hello world!")""",
    """@deprecated
      |def func17()
      |""",
    """@deprecated
      |def func18(): Unit
      |""",
    """@deprecated
      |def func19() = println("Hello world!")
      |""",
    """@deprecated
      |def func20(): Unit = println("Hello world!")
      |""",
    """@deprecated
      |def func21[T]()
      |""",
    """@deprecated
      |def func22[T](): Unit
      |""",
    """@deprecated
      |def func23[T]() = println("Hello world!")
      |""",
    """@deprecated
      |def func24[T](): Unit = println("Hello world!")
      |""",
    """@deprecated
      |protected def func25()
      |""",
    """@deprecated
      |protected def func26(): Unit
      |""",
    """@deprecated
      |protected def func27() = println("Hello world!")
      |""",
    """@deprecated
      |protected def func28(): Unit = println("Hello world!")
      |""",
    """@deprecated
      |protected def func29[T]()
      |""",
    """@deprecated
      |protected def func30[T](): Unit
      |""",
    """@deprecated
      |protected def func31[T]() = println("Hello world!")
      |""",
    """@deprecated
      |protected def func32[T](): Unit = println("Hello world!")
      |""",
    """@deprecated @native
      |protected override def func33[T](): Unit = println("Hello world!")
      |"""
  )

  def testNoChange(): Unit = {
    content.map(_.stripMargin.strip())
      .foreach(line => {
      replaceAndAssert(
        s"No content change no variable, line: $line",
        content.mkString("\n"), line, line, content.mkString("\n")
      )
    })
  }

  def testNoChangeVariables(): Unit = {
    val patterns = List(
      ("def $func$()", "def $func$(a: Int)"),
      ("@$anno$\nprotected def $func$[$T$](): $ret$ = $body$", "@$anno$\nprotected def $func$[$T$](a: Int): $ret$ = $body$"),
      ("def $func$() = $body$", "def $func$(a: Int) = $body$"),
      ("def $func$(): $ret$", "def $func$(a: Int): $ret$"),
      ("@$anno$\ndef $func$(): $ret$ = $body$", "@$anno$\ndef $func$(a: Int): $ret$ = $body$"),
      ("protected def $func$(): $ret$ = $body$", "protected def $func$(a: Int): $ret$ = $body$")
    )
    for ((spattern, rpattern) <- patterns) {
      val exp = content
          .map(_.stripMargin.strip())
          .map(line => if !spattern.contains("protected") || line.contains("protected") then line.replace("()", "(a: Int)") else line)
          .mkString("\n")
      replaceAndAssert(
        s"No content change with variables for pattern <$spattern>",
        content.map(_.stripMargin.strip()).mkString("\n"),
        spattern,
        rpattern,
        if spattern.contains("protected") then exp.replace("override ", "") else exp,
        mO => {
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$T$")) constrCount(mO, "T")
          if (spattern.contains("$ret$")) constrCount(mO, "ret", 0, 1)
          if (spattern.contains("$body$")) constrCount(mO, "body", 0, 1)
        }
      )
    }
  }

  def testChangeVariables(): Unit = {
    val patterns = List(
      ("def $func$()", "protected def $func$(a: Int)", (line: String) =>
        (if line.contains("def") && !line.contains("protected") then line.replace("def", "protected def") else line).replace("override ", "")),
      ("@$anno$\nprotected def $func$[$T$](): $ret$ = $body$", "protected def $func$[$T$](a: Int): $ret$ = $body$", (line: String) =>
        (if line.contains("protected") then line.replace("@deprecated", "").replace("@native", "").strip() else line).replace("override ", "")),
      ("def $func$() = $body$", "def $func$(a: Int)", (line: String) => line.replace(" = println(\"Hello world!\")", "")),
      ("def $func$(): $ret$", "def $func$(a: Int)", (line: String) => line.replace(": Unit", "")),
      ("@$anno$\ndef $func$(): $ret$ = $body$", "@$anno$\ndef $func$(a: Int): Int = 1", (line: String) =>
        line.replace(": Unit", "").replace(" = println(\"Hello world!\")", "").replace(")", "): Int = 1")),
      ("def $func$(): $ret$ = $body$", "protected def $func$[T](a: Int): $ret$ = $body$", (line: String) =>
        line.replace("[T]", "").replace("(a", "[T](a").replace("protected ", "").replace("override ", "").replace("def", "protected def")),
      ("def $func$()", "@deprecated\ndef $func$(a: Int)", (line: String) =>
        "@deprecated\n" + line.split("\n").last)
    )
    for ((spattern, rpattern, f) <- patterns) {
      val exp = content
        .map(_.stripMargin.strip())
        .map(line => if !spattern.contains("protected") || line.contains("protected") then line.replace("()", "(a: Int)") else line)
        .map(f)
        .mkString("\n")
      replaceAndAssert(
        s"No content change with variables for pattern <$spattern>",
        content.map(_.stripMargin.strip()).mkString("\n"),
        spattern,
        rpattern,
        if spattern.contains("protected") then exp.replace("override ", "") else exp,
        mO => {
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$T$")) constrCount(mO, "T")
          if (spattern.contains("$ret$")) constrCount(mO, "ret", 0, 1)
          if (spattern.contains("$body$")) constrCount(mO, "body", 0, 1)
        }
      )
    }
  }

  def testFuncCount(): Unit = {
    val content =
      """def func234(): Unit = {
        |  def func1(): Unit = {
        |    println("Hi")
        |    println("Welt")
        |    println("!")
        |  }
        |  def func2(): Int = {
        |    println("Hi")
        |    2
        |  }
        |  def func3(): String = {
        |    println("Hi")
        |    "Hi"
        |  }
        |}
        |"""
    val pattern =
      """def func234(): Unit = {
        |  def $func$(): $ty$ = {
        |    $expr$
        |  }
        |}
        |"""

    replaceAndAssert(
      "Match functions with count no change",
      content, pattern, pattern, content,
      mO => {
        constrCount(mO, "func")
        constrCount(mO, "expr")
      }
    )

    val rpattern =
      """def func234(): Unit = {
        |  def $func$(): Int = {
        |    println("JetBrains")
        |    $expr$
        |    42
        |  }
        |}
        |"""
    val exp =
      """def func234(): Unit = {
        |  def func1(): Int = {
        |    println("JetBrains")
        |    println("Hi")
        |    println("Welt")
        |    println("!")
        |    42
        |  }
        |  def func2(): Int = {
        |    println("JetBrains")
        |    println("Hi")
        |    2
        |    42
        |  }
        |  def func3(): Int = {
        |    println("JetBrains")
        |    println("Hi")
        |    "Hi"
        |    42
        |  }
        |}
        |"""
    replaceAndAssert(
      "Match functions with count some change",
      content, pattern, rpattern, exp,
      mO => {
        constrCount(mO, "func")
        constrCount(mO, "expr")
      }
    )
  }

  private def constrCount(matchOptions: MatchOptions, name: String, min: Int = 0, max: Int = 100): Unit = {
    val constrBody = matchOptions.addNewVariableConstraint(name)
    constrBody.setMinCount(min)
    constrBody.setMaxCount(max)
  }
}
