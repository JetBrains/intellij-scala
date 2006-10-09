package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder

class Package {

  def parse(builder: PsiBuilder): Unit = {

    var marker = builder.mark() //Open marker for package

    builder.advanceLexer //New node: "package"

  Console.println("token : " + builder.getTokenType)
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => new QualId parse(builder) //QualID found
      case _ => builder.error("Wrong package name!")
    }

    marker.done(ScalaElementTypes.PACKAGE) //Close marker for package
  }
}