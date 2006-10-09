package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder

class Package {

  def parse(builder: PsiBuilder): Unit = {

    var pgMarker = builder.mark() //Open marker for package group

    val packMarker = builder.mark()
    builder.advanceLexer //New node: "package"
    packMarker.done(ScalaElementTypes.PACKAGE)

    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        val qualIDmarker = builder.mark()
        builder.advanceLexer   // Ate QualID identifier
        (new QualId).parse( builder , qualIDmarker )  //QualID found
      }
      case _ => builder.error("Wrong package name")
    }
    pgMarker.done(ScalaElementTypes.PACKAGE_GROUP) //Close marker for package
  }
}