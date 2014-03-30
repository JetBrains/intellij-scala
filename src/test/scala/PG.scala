import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.pom.java.LanguageLevel

object PG {
  def main(args: Array[String]) {
    val javaLexer = new JavaLexer(LanguageLevel.JDK_1_8)

    javaLexer.start(
      """
        |package com.ghik;
        |
        |public class Costam {
        |    public Costam() {
        |        System.out.println("Srsly dude, wtf!");
        |    }
        |}
        |
      """.stripMargin)

    while (javaLexer.getTokenType != null) {
      println(javaLexer.getTokenType + ": [" + javaLexer.getTokenText.replaceAllLiterally("\n", "\\n") + "]")
      javaLexer.advance()
    }

  }

}
