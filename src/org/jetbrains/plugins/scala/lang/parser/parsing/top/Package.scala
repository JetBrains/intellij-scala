package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder

//import org.jetbrains.plugins.scala.lang.parser.parsing.CompilationUnit.QualId
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
/*
object Package extends Constr {

  override def parse(builder: PsiBuilder): Unit = {

      builder.getTokenType() match {
        case ScalaTokenTypes.kPACKAGE => {
          val packMarker = builder.mark()
          Console.println("expected token 'package' " + builder.getTokenType())
          builder.advanceLexer //New node: "package"
          packMarker.done(ScalaElementTypes.PACKAGE)

          builder.getTokenType() match {
            case ScalaTokenTypes.tIDENTIFIER => {
            Console.println("expected token 'identifier' " + builder.getTokenType())
              val qualIDmarker = builder.mark()

              val idMarker = builder.mark()
              builder.advanceLexer // Ate QualID identifier
              idMarker.done(ScalaTokenTypes.tIDENTIFIER)

              QualId.parse( builder )  //QualID found
              qualIDmarker.done(ScalaElementTypes.QUAL_ID)

            }
            case _ => builder.error("Wrong package name")
          }
        }

        case _ => { builder.error("expected token 'package'") }
      }


  }
}  */