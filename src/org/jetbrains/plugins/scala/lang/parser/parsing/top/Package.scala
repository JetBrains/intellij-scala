package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder

class Package {

  def parse(builder: PsiBuilder): Unit = {

    var marker = builder.mark() //Open marker for package group

    val packMarker = builder.mark()
    builder.advanceLexer //New node: "package"
    packMarker.done(ScalaElementTypes.PACKAGE_GROUP)

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val marker = builder.mark()
        builder.advanceLexer   // Ate QualID identifier
        (new QualId).parse( builder , marker )  //QualID found
      }
      case _ => builder.error("Wrong package name")
    }
    marker.done(ScalaElementTypes.PACKAGE_GROUP) //Close marker for package
  }
}