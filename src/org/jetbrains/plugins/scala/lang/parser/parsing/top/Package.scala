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

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val marker = builder.mark()
        (new QualId).parse(builder,marker)  //QualID found
        marker.done(ScalaElementTypes.QUALID)
      }
      case _ => builder.error("Wrong package name")
    }

    marker.done(ScalaElementTypes.PACKAGE) //Close marker for package
  }
}