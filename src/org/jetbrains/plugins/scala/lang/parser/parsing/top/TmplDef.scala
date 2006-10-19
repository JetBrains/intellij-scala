package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType

/**
 * User: Dmitry.Krasilschikov
 * Date: 16.10.2006
 * Time: 13:54:45
 */

 /*
 TmplDef ::= [case] class ClassDef
           | [case] object ObjectDef
           | trait TraitDef

*/

object TmplDef extends Constr{
  abstract class TypeDef {
      def getKeyword : ScalaElementType

      def getDef :  ScalaElementType

      def parseDef ( builder : PsiBuilder ) : Unit

      def parse ( builder : PsiBuilder ) = {

        //node : keyword
        val keywordMarker = builder.mark()
        builder.advanceLexer
        keywordMarker.done(getKeyword)

        //definition of class
        val defMarker = builder.mark()
        parseDef ( builder )
        defMarker.done( getDef )


        //body of class
        /*val bodyMarker = builder.mark()
        parseBody ( builder )
        bodyMarker.done( getBody )
          */
      }
  }

  abstract class InstanceDef extends TypeDef

  case object ObjectDef extends InstanceDef {
    def getKeyword = ScalaElementTypes.OBJECT

    def getDef : ScalaElementType = ScalaElementTypes.OBJECT_DEF

    def parseDef ( builder : PsiBuilder ) : Unit = {
        while ( !builder.getTokenType.equals(ScalaTokenTypes.tLBRACE) ){
        builder.advanceLexer
      }
    }

  }

  case class ClassDef extends InstanceDef {
    def getKeyword = ScalaElementTypes.CLASS

    def getDef = ScalaElementTypes.CLASS_DEF

    def parseDef ( builder : PsiBuilder ) : Unit = {
      while ( !builder.getTokenType.equals(ScalaTokenTypes.tLBRACE) ){
        builder.advanceLexer
      }
    }
  }

  case class TraitDef extends TypeDef {
    def getKeyword = ScalaElementTypes.TRAIT

    def getDef = ScalaElementTypes.TRAIT_DEF

    def parseDef ( builder : PsiBuilder ) : Unit = {

      while ( !builder.getTokenType.equals(ScalaTokenTypes.tLBRACE) ){
        builder.advanceLexer
      }
    }
  }

  override def parse(builder : PsiBuilder) : Unit = {
    def parseInst ( builder : PsiBuilder ) : Unit = {
        Console.println("token type : " + builder.getTokenType())
        builder.getTokenType() match {
          case ScalaTokenTypes.kCLASS => {
            val classStmtMarker = builder.mark()

          //  builder.advanceLexer

            ClassDef.parse( builder )

            classStmtMarker.done(ScalaElementTypes.CLASS_STMT)
          }

          case ScalaTokenTypes.kOBJECT => {
              val objectStmtMarker = builder.mark()
          //    builder.advanceLexer

              ObjectDef.parse( builder )

              objectStmtMarker.done(ScalaElementTypes.OBJECT_STMT)
          }
        }
    }

//    val tmplDefMarker = builder.mark()

    Console.println("token type : " + builder.getTokenType())
    builder.getTokenType() match {
      case ScalaTokenTypes.kCASE => {
        val caseMarker = builder.mark();
        builder.advanceLexer //Ate case

        caseMarker.done(ScalaElementTypes.CASE);

        parseInst( builder ) //handle class and object
      }

      case ScalaTokenTypes.kOBJECT | ScalaTokenTypes.kCLASS => {
        parseInst( builder ) //handle class and object
      }

      case ScalaTokenTypes.kTRAIT => {
        val traitStmtMarker = builder.mark();

        TraitDef.parse( builder )

        traitStmtMarker.done(ScalaElementTypes.TRAIT_STMT);
      }

      case _ => builder.error("wrong type definition")

    }

//    tmplDefMarker.done(ScalaElementTypes.TMPL_DEF)
    //Console.println("tmplDefMareker done ")
  }
}
