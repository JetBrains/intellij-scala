package org.jetbrains.plugins.scala.structuralSearch.replace

import com.intellij.structuralsearch.MatchOptions
import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralReplaceTestCase

class ScSRMatchTest extends ScalaStructuralReplaceTestCase {

  val content = Seq(
    """a match {
      |  case None => println("Jet")
      |  case Some(i) if i > 100 => println("Brains")
      |  case _ => println("Scala!")
      |}
      |""",
    """a match {
      |  case None => println("Jet")
      |}
      |""",
    """a match {
      |  case None => println("Jet")
      |  case Some(i) if i > 101 => println("Brains")
      |  case Some(i) if i > 100 => println("TUM")
      |  case _ =>
      |}
      |""",
    """a match {
      |}
      |""",
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
      ("""$a$ match {
         |  case $case$ if $guard$ => $expr$
         |}
         |""",
       """b match {
         |  case $case$ if $guard$ => $expr$
         |}
         |"""),
      ("""$a$ match {
         |  case $case$ => $expr$
         |}
         |""",
        """b match {
          |  case $case$ => $expr$
          |}
          |""")
    )
    for ((spattern, rpattern) <- patterns) {
      replaceAndAssert(
        s"No content change with variables for pattern <${spattern.stripMargin}>",
        content.map(_.stripMargin.strip()).mkString("\n"),
        spattern, rpattern,
        content.map(_.stripMargin.strip()).map(_.replace("a ", "b ")).mkString("\n")
          .replace("{\n}", "{\n  \n}")
          .replace("=>\n", "=> \n"),
        mO => {
          if (spattern.contains("$case$")) constrCount(mO, "case")
          if (spattern.contains("$guard$")) constrCount(mO, "guard", 0, 1)
          if (spattern.contains("$expr$")) constrCount(mO, "expr")
        }
      )
    }
  }

  def testChangeVariables(): Unit = {
    val patterns = List(
      // add additional expr
      ("""$ae$ match {
         |  case $case$ if $guard$ => $expr$
         |}
         |""",
        """b match {
          |  case $case$ if $guard$ => {
          |    println("another print")
          |    $expr$
          |  }
          |}
          |""",
        (_: String)
          .replace("=> ",
            """=> {
              |    println("another print")
              |    """.stripMargin)
          .replace("=>\n",
            """=> {
              |    println("another print")
              |""".stripMargin + "    \n")
          .replace("{\n}", "emptyBody")
          .replace("}",
            """  }
              |}""".stripMargin)
          .replace("emptyBody", "{\n  \n}")
          .replaceFirst("case", "cas1")
          .replace("case",
            """}
              |  case
              |""".stripMargin.strip)
          .replaceFirst("cas1", "case")),
      // add expr with 0 1 var
      ("""$aezo$ match {
         |  case $case$ if $guard$ => $expr$
         |}
         |""",
        """b match {
          |  case $case$ if $guard$ => println("another print")
          |}
          |""",
        (_: String)
          .split("\n")
          .map(line => {
            if (line.contains("=>"))
              line.split("=>").head + "=> println(\"another print\")"
            else
              line
          })
          .mkString("\n")
          .replace("{\n}", "{\n  \n}")),
      // add expr not in search
      ("""$aens$ match {
         |  case $case$ if $guard$
         |}
         |""",
        """b match {
          |  case $case$ if $guard$ => println("another print")
          |}
          |""",
        (_: String)
          .split("\n")
          .map(line => {
            if (line.contains("=>"))
              line.split("=>").head + "=> println(\"another print\")"
            else
              line
          })
          .mkString("\n")
          .replace("{\n}", "{\n  \n}")),
      // remove guard
      ("""$rg$ match {
         |  case $case$ if $guard$ => $expr$
         |}
         |""",
        """b match {
          |  case $case$ => $expr$
          |}
          |""",
          (_: String).replaceAll("if i > 10. =", "=")
            .replace("{\n}", "{\n  \n}")
            .replace("=>\n", "=> \n")),
      // add guard with 0 1 var
      ("""$agzo$ match {
         |  case $case$ if $guard$ => $expr$
         |}
         |""",
        """b match {
          |  case $case$ if i > 102 => $expr$
          |}
          |""",
        (_: String)
          .replaceAll("if i > 10. =", "=")
          .replace("=>", "if i > 102 =>")
          .replace("{\n}", "{\n  \n}")
          .replace("=>\n", "=> \n")),
      // add guard not in search
      ("""$agns$ match {
         |  case $case$ => $expr$
         |}
         |""",
        """b match {
          |  case $case$ if i > 102 => $expr$
          |}
          |""",
        (_: String)
          .replaceAll("if i > 10. =", "=")
          .replace("=>", "if i > 102 =>")
          .replace("{\n}", "{\n  \n}")
          .replace("=>\n", "=> \n"))
    )
    for ((spattern, rpattern, f) <- patterns) {
      replaceAndAssert(
        s"No content change with variables for pattern <${spattern.stripMargin.strip}>",
        content.map(_.stripMargin.strip()).mkString("\n"),
        spattern,
        rpattern,
        content.map(_.stripMargin.strip()).map(_.replace("a ", "b ")).map(f).mkString("\n"),
        mO => {
          if (spattern.contains("$case$")) constrCount(mO, "case")
          if (spattern.contains("$guard$")) constrCount(mO, "guard", 0, 1)
          if (spattern.contains("$expr$")) constrCount(mO, "expr")
        }
      )
    }
  }

  def testMoveLastup(): Unit = {
    val spattern =
      """$a$ match {
        |  case $fCase$ => $fExpr$
        |  case $case$ => $expr$
        |}
        |"""
    val rpattern =
      """b match {
        |  case $case$ => $expr$
        |  case $fCase$ => $fExpr$
        |}
        |"""
    val exp =
      """b match {
        |  case Some(i) if i > 100 => println("Brains")
        |  case _ => println("Scala!")
        |  case None => println("Jet")
        |}
        |b match {
        |spacespace
        |  case None => println("Jet")
        |}
        |b match {
        |  case Some(i) if i > 101 => println("Brains")
        |  case Some(i) if i > 100 => println("TUM")
        |  case _ =>space
        |  case None => println("Jet")
        |}
        |a match {
        |}
        |""".replace("space", " ")

    replaceAndAssert(
      s"No content change with variables for pattern <${spattern.stripMargin}>",
      content.map(_.stripMargin.strip).mkString("\n"),
      spattern,
      rpattern,
      exp,
      mO => {
        if (spattern.contains("$case$")) constrCount(mO, "case")
        if (spattern.contains("$guard$")) constrCount(mO, "guard", 0, 1)
        if (spattern.contains("$expr$")) constrCount(mO, "expr")
      }
    )
  }

  private def constrCount(matchOptions: MatchOptions, name: String, min: Int = 0, max: Int = 100): Unit = {
    val constrBody = matchOptions.addNewVariableConstraint(name)
    constrBody.setMinCount(min)
    constrBody.setMaxCount(max)
  }
}
