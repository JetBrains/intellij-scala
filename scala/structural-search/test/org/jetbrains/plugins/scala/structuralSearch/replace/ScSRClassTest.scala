package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.structuralsearch.MatchOptions
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralReplaceTestCase

class ScSRClassTest extends ScalaStructuralReplaceTestCase {

  def testNoChange(): Unit = {
    content.map(_.stripMargin.strip())
      .foreach(line => {
        replaceAndAssert(
          s"No content change no variable, line: $line",
          content.mkString("\n"), line, line, content.mkString("\n")
        )
      })
    val text = content.mkString("\n")
    replaceAndAssert(
      s"No content change no variable, whole text",
      text, text, text, text
    )
  }

  def testCopyAll(): Unit = {
    replaceAndAssert(
      s"No content change with variables for copy all",
      content.last,
      """@$anno$
        |private abstract class $class$[$typs$]($para$) extends $ext$ {
        |  var $var$
        |  def $func$()
        |  class $subClass$
        |}
        |""",
      """@$anno$
        |private abstract trait $class$[$typs$]($para$) extends $ext$ {
        |  var $var$
        |  def $func$()
        |  class $subClass$
        |}
        |""",
      content.last
        .replace("class class", "trait class"),
      mO => {
        constrCount(mO, "anno")
        constrCount(mO, "typs")
        constrCount(mO, "para")
        constrCount(mO, "ext")
        constrCount(mO, "var")
        constrCount(mO, "func")
        constrCount(mO, "subClass")
      }
    )
  }

  def testNoChangeVariables(): Unit = {
    val patterns = List(
      ("""class $class$ {
         |}
         |""",
        """trait $class$ {
          |}
          |"""),
      ("""@$anno$
         |private abstract class $class$[$typs$]($para$) extends $ext$ {
         |  var $var$
         |  def $func$()
         |  class $subClass$
         |}
         |""",
        """@$anno$
          |private abstract trait $class$[$typs$]($para$) extends $ext$ {
          |  var $var$
          |  def $func$()
          |  class $subClass$
          |}
          |"""
      ),
      ("""
         |private abstract class $class$($para$) {
         |  def $func$()
         |  class $subClass$
         |}
         |""",
        """
          |private abstract trait $class$($para$) {
          |  def $func$()
          |  class $subClass$
          |}
          |"""
      ),
      ("""@$anno$
         |class $class$[$typs$] extends $ext$ {
         |  class $subClass$
         |}
         |""",
        """@$anno$
          |trait $class$[$typs$] extends $ext$ {
          |  class $subClass$
          |}
          |"""
      ),
    )
    for ((spattern, rpattern) <- patterns) {
      val exp = content
          .map(_.stripMargin.strip())
          .map(line => if !spattern.contains("private") || line.contains("private") then line.replace("class class", "trait class") else line)
          .mkString("\n")
      replaceAndAssert(
        s"No content change with variables for pattern <$spattern>",
        content.map(_.stripMargin.strip()).mkString("\n"),
        spattern,
        rpattern,
        exp,
        mO => {
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$typs$")) constrCount(mO, "typs")
          if (spattern.contains("$para$")) constrCount(mO, "para")
          if (spattern.contains("$ext$")) constrCount(mO, "ext")
          if (spattern.contains("$var$")) constrCount(mO, "var")
          if (spattern.contains("$func$")) constrCount(mO, "func")
          if (spattern.contains("$subClass$")) constrCount(mO, "subClass")
        }
      )
    }
  }

  val a =
    """@deprecated @noinline
      |private abstract class class255[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |"""

  def testChangeVariables(): Unit = {
    val patterns = List(
      ("""@$anno$
         |private abstract class $class$[$typs$]($para$) extends $ext$ {
         |  var $var$
         |  def $func$()
         |  class $subClass$
         |}
         |""",
        """trait $class$ {
          |}
          |""",
        (line: String) => {
          if (line.contains("private abstract"))
            line
              .replace("@deprecated @noinline\n", "")
              .replace("private abstract ", "")
              .replace("[T, R]", "")
              .replace("(val a: Int, var b: String = \"Nee\", c: Double = 4.2)", "")
              .replace("extends A(a, c), B ", "")
              .replace("\n  var d: Int = 32", "")
              .replace("\n  def shoutMe(): Unit = println(b)", "")
              .replace("\n  class subClass extends C {\n    def test(): Unit = println(\"Testing started...\")\n  }", "")
              .replace("class class", "trait class")
          else
            line
        }
      ),
      ("""@$anno$
         |class $class$[$typs$]($para$) extends $ext$ {
         |  var $var$
         |  def $func$()
         |  class $subClass$
         |}
         |""",
        """trait $class$ {
          |}
          |""",
        (_: String)
          .replace("@deprecated @noinline\n", "")
          .replace("[T, R]", "")
          .replace("(val a: Int, var b: String = \"Nee\", c: Double = 4.2)", "")
          .replace("extends A(a, c), B ", "")
          .replace("\n  var d: Int = 32", "")
          .replace("\n  def shoutMe(): Unit = println(b)", "")
          .replace("\n  class subClass extends C {\n    def test(): Unit = println(\"Testing started...\")\n  }", "")
          .replace("class class", "trait class")
      ),
      (
        """class $class$ {
          |}
          |""",
        """@deprecated @noinline
          |private abstract trait $class$[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
          |  var d: Int = 32
          |  def shoutMe(): Unit = println(b)
          |  class subClass extends C {
          |    def test(): Unit = println("Testing started...")
          |  }
          |}
          |""",
        (line: String) => {
          val num = line.split("class class").tail.head.substring(0, 3)
          """@deprecated @noinline
            |private abstract trait class$id$[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
            |  var d: Int = 32
            |  def shoutMe(): Unit = println(b)
            |  class subClass extends C {
            |    def test(): Unit = println("Testing started...")
            |  }
            |}
            |""".stripMargin.strip.replace("$id$", num)
        }
      ),
    )
    for ((spattern, rpattern, f) <- patterns) {
      val exp = content
        .map(_.stripMargin.strip())
        .map(f)
        .mkString("\n")
      replaceAndAssert(
        s"No content change with variables for pattern <$spattern>",
        content.map(_.stripMargin.strip()).mkString("\n"),
        spattern,
        rpattern,
        exp,
        mO => {
          if (spattern.contains("$anno$")) constrCount(mO, "anno")
          if (spattern.contains("$typs$")) constrCount(mO, "typs")
          if (spattern.contains("$para$")) constrCount(mO, "para")
          if (spattern.contains("$ext$")) constrCount(mO, "ext")
          if (spattern.contains("$var$")) constrCount(mO, "var")
          if (spattern.contains("$func$")) constrCount(mO, "func")
          if (spattern.contains("$subClass$")) constrCount(mO, "subClass")
        }
      )
    }
  }

  private def constrCount(matchOptions: MatchOptions, name: String, min: Int = 0, max: Int = 100): Unit = {
    val constrBody = matchOptions.addNewVariableConstraint(name)
    constrBody.setMinCount(min)
    constrBody.setMaxCount(max)
  }

  val content: Seq[String] = Seq(
    """class class000 {
      |}
      |""",
    """class class001 extends A(a, c), B {
      |}
      |""",
    """class class002 {
      |  var d: Int = 32
      |}
      |""",
    """class class003 extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """class class004 {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class005 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class006 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class007 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class008 {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class009 extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class010 {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class011 extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class012 {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class013 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class014 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class015 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class016(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """class class017(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """class class018(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """class class019(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """class class020(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class021(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class022(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class023(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class024(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class025(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class026(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class027(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class028(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class029(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class030(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class031(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class032[T, R] {
      |}
      |""",
    """class class033[T, R] extends A(a, c), B {
      |}
      |""",
    """class class034[T, R] {
      |  var d: Int = 32
      |}
      |""",
    """class class035[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """class class036[T, R] {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class037[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class038[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class039[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class040[T, R] {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class041[T, R] extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class042[T, R] {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class043[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class044[T, R] {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class045[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class046[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class047[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class048[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """class class049[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """class class050[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """class class051[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """class class052[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class053[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class054[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class055[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """class class056[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class057[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class058[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class059[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class060[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class061[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class062[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """class class063[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class064 {
      |}
      |""",
    """@deprecated @noinline
      |class class065 extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |class class066 {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class067 extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class068 {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class069 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class070 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class071 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class072 {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class073 extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class074 {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class075 extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class076 {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class077 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class078 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class079 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class080(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """@deprecated @noinline
      |class class081(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |class class082(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class083(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class084(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class085(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class086(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class087(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class088(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class089(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class090(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class091(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class092(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class093(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class094(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class095(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class096[T, R] {
      |}
      |""",
    """@deprecated @noinline
      |class class097[T, R] extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |class class098[T, R] {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class099[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class100[T, R] {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class101[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class102[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class103[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class104[T, R] {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class105[T, R] extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class106[T, R] {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class107[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class108[T, R] {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class109[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class110[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class111[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class112[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """@deprecated @noinline
      |class class113[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |class class114[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class115[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |class class116[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class117[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class118[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class119[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |class class120[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class121[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class122[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class123[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class124[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class125[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class126[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |class class127[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class128 {
      |}
      |""",
    """private abstract class class129 extends A(a, c), B {
      |}
      |""",
    """private abstract class class130 {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class131 extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class132 {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class133 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class134 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class135 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class136 {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class137 extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class138 {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class139 extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class140 {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class141 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class142 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class143 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class144(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """private abstract class class145(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """private abstract class class146(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class147(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class148(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class149(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class150(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class151(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class152(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class153(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class154(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class155(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class156(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class157(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class158(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class159(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class160[T, R] {
      |}
      |""",
    """private abstract class class161[T, R] extends A(a, c), B {
      |}
      |""",
    """private abstract class class162[T, R] {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class163[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class164[T, R] {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class165[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class166[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class167[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class168[T, R] {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class169[T, R] extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class170[T, R] {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class171[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class172[T, R] {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class173[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class174[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class175[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class176[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """private abstract class class177[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """private abstract class class178[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class179[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """private abstract class class180[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class181[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class182[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class183[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """private abstract class class184[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class185[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class186[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class187[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class188[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class189[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class190[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """private abstract class class191[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class192 {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class193 extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class194 {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class195 extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class196 {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class197 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class198 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class199 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class200 {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class201 extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class202 {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class203 extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class204 {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class205 extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class206 {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class207 extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class208(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class209(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class210(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class211(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class212(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class213(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class214(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class215(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class216(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class217(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class218(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class219(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class220(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class221(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class222(val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class223(val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class224[T, R] {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class225[T, R] extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class226[T, R] {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class227[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class228[T, R] {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class229[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class230[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class231[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class232[T, R] {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class233[T, R] extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class234[T, R] {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class235[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class236[T, R] {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class237[T, R] extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class238[T, R] {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class239[T, R] extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class240[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class241[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class242[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class243[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class244[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class245[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class246[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class247[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class248[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class249[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class250[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class251[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class252[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class253[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class254[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |""",
    """@deprecated @noinline
      |private abstract class class255[T, R](val a: Int, var b: String = "Nee", c: Double = 4.2) extends A(a, c), B {
      |  var d: Int = 32
      |  def shoutMe(): Unit = println(b)
      |  class subClass extends C {
      |    def test(): Unit = println("Testing started...")
      |  }
      |}
      |"""
  )
}
