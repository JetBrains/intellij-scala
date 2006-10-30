package org.jetbrains.plugins.scala.lang.parser.parsing.top

import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Construction
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
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
      def getKeyword : IElementType

      def getDef :  IElementType

      def parseDef ( builder : PsiBuilder ) : Unit

      def parse ( builder : PsiBuilder ) = {

        val tmplDefMarker = builder.mark()
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
        tmplDefMarker.done(ScalaElementTypes.TMPL_DEF)
      }
  }

  abstract class InstanceDef extends TypeDef

  case object ObjectDef extends InstanceDef {
    def getKeyword = ScalaTokenTypes.kOBJECT

    def getDef : ScalaElementType = ScalaElementTypes.OBJECT_DEF

    def parseDef ( builder : PsiBuilder ) : Unit = {

      if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
      } else builder.error("expected identifier")

      builder.getTokenType match {
        case ScalaTokenTypes.kEXTENDS
           | ScalaTokenTypes.tLINE_TERMINATOR
           | ScalaTokenTypes.tLBRACE
          => {
           ClassTemplate.parse(builder)
        }
        
        case _ => {}
      }
    }
  }

    def checkForTypeParamClauses (first : IElementType, second : IElementType) : Boolean = {
      var a = first
      var b = second

      if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
        a = b
      }

      a.equals(ScalaTokenTypes.tLSQBRACKET)
    }

    def checkForClassParamClauses(first : IElementType, second : IElementType) : Boolean = {
      var a = first
      var b = second

      if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
        a = b
      }

      if (a.equals(ScalaTokenTypes.tLPARENTHIS)) true

      false
    }

    def checkForClassParamClause(first : IElementType, second : IElementType, third : IElementType) : Boolean = {
      var a = first
      var b = second
      var c = third
      if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
        a = b
        b = c
      }

      a.equals(ScalaTokenTypes.tLPARENTHIS) && (b.equals(ScalaTokenTypes.kVAL)
         || b.equals(ScalaTokenTypes.kVAR       )
         || b.equals(ScalaTokenTypes.kABSTRACT  )
         || b.equals(ScalaTokenTypes.kFINAL     )
         || b.equals(ScalaTokenTypes.kOVERRIDE  )
         || b.equals(ScalaTokenTypes.kPRIVATE   )
         || b.equals(ScalaTokenTypes.kPROTECTED )
         || b.equals(ScalaTokenTypes.kSEALED    )
         || b.equals(ScalaTokenTypes.tIDENTIFIER)
         )


    }

    def checkForImplicit(first : IElementType, second : IElementType, third : IElementType) : Boolean = {
      var a = first
      var b = second
      var c = third
      if (a.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
        a = b
        b = c
      }

      a.equals(ScalaTokenTypes.tLPARENTHIS) && b.equals(ScalaTokenTypes.kIMPLICIT)

    }


    case class ClassDef extends InstanceDef {
      def getKeyword = ScalaTokenTypes.kCLASS

      def getDef = ScalaElementTypes.CLASS_DEF

      def parseDef ( builder : PsiBuilder ) : Unit = {
        
        Console.println("expected identifier " + builder.getTokenType)
        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

          var chooseParsingWay = builder.mark()

          builder.advanceLexer
          val first = builder.getTokenType()
          Console.println("first " + first)

          builder.advanceLexer
          val second = builder.getTokenType()
          Console.println("second " + second)

          chooseParsingWay.rollbackTo()

          if (checkForTypeParamClauses(first, second)) {
            Console.println("checked for type parameters ")
            TypeParamClause.parse(builder)
          }

          ClassParamClauses.parse(builder)

          if (builder.getTokenType.equals(ScalaTokenTypes.kREQUIRES)) {
          //todo check
            SimpleType.parse(builder)
          }
          Console.println("cltemple expect " + builder.getTokenType)
          builder.getTokenType match {
            case ScalaTokenTypes.kEXTENDS
               | ScalaTokenTypes.tLINE_TERMINATOR
               | ScalaTokenTypes.tLBRACE
              => {
               ClassTemplate.parse(builder)
            }
            case _ => {}
          }
        }


      }
   }

    object ClassTemplate extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val classTemplateMarker = builder.mark()

        if (builder.getTokenType.equals(ScalaTokenTypes.kEXTENDS)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.kEXTENDS)

          if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)){
            TemplateParents.parse(builder)
          } else builder.error("expected identifier")
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tLBRACE)){
            TemplateBody.parse(builder)
        } else if (builder.getTokenType.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)

          if (builder.getTokenType.equals(ScalaTokenTypes.tLBRACE)){
            TemplateBody.parse(builder)
          } else builder.error("expected '{'")
        }

        classTemplateMarker.done(ScalaElementTypes.CLASS_TEMPLATE)
      }
    }

    object TemplateParents extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val templateParents = builder.mark()

        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          Construction.parse(builder)
        } else builder.error("expected identifier")

        while (builder.getTokenType.equals(ScalaTokenTypes.kWITH)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kWITH)

          //todo check
          SimpleType.parse(builder)
        }

        templateParents.done(ScalaElementTypes.TEMPLATE_PARENTS)
      }
    }

    object TemplateBody extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val templateBody = builder.mark()

        if (builder.getTokenType.equals(ScalaTokenTypes.tLBRACE)) {
          //ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)
         //todo
          var counter = 1
          var lastRBrace = false

          while ( !builder.eof() && !lastRBrace){
            builder.advanceLexer
            val token = builder.getTokenType
            if (token.equals(ScalaTokenTypes.tLBRACE)) {
            Console.println("ate '{'")
              counter = counter + 1
            }


            if (token.equals(ScalaTokenTypes.tRBRACE)) {
            Console.println("ate '}'")
              counter = counter - 1
            }

            if (counter == 0) {
              lastRBrace = true
              builder.advanceLexer
            }
          }
         //

        }

        templateBody.done(ScalaElementTypes.TEMPLATE_BODY)
      }

   /*   override def isParsible(CharSequence buffer, val Project project) = {

      }
     */

    }


    object TypeParamClause extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val typeParamClause = builder.mark()

        if (builder.getTokenType.equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tLSQBRACKET)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tPLUS
               | ScalaTokenTypes.tMINUS
               | ScalaTokenTypes.tIDENTIFIER
               => {
              VariantTypeParams.parse(builder)
           }
        }

        } else builder.error("expected '[")

        if (builder.getTokenType.equals(ScalaTokenTypes.tRSQBRACKET)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
        } else builder.error("expected ']")

        typeParamClause.done(ScalaElementTypes.TYPE_PARAM_CLAUSE)
      }
    }

    object VariantTypeParams extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val varTypeParamsClause = builder.mark()

        builder.getTokenType match {
          case ScalaTokenTypes.tPLUS
             | ScalaTokenTypes.tMINUS
             | ScalaTokenTypes.tIDENTIFIER
             => {
            VariantTypeParam.parse(builder)
          }
        }

        while (builder.getTokenType.equals(ScalaTokenTypes.tCOMMA)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

          VariantTypeParam.parse(builder)
        }

        varTypeParamsClause.done(ScalaElementTypes.VARIANT_TYPE_PARAMS)
      }
    }

    object VariantTypeParam extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val varTypeParamClause = builder.mark()

        if (!builder.getTokenType.equals(ScalaTokenTypes.tPLUS)
            && !builder.getTokenType.equals(ScalaTokenTypes.tMINUS)
            && !builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)){
          builder.error("expected '+', '-' or identifier")
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tPLUS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tPLUS)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tMINUS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tMINUS)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)) {
          TypeParam.parse(builder)
        }

        varTypeParamClause.done(ScalaElementTypes.VARIANT_TYPE_PARAM)
      }
    }

    object TypeParam extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val typeParamClause = builder.mark()

        if (builder.getTokenType.equals(ScalaTokenTypes.tIDENTIFIER)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        } else builder.error("expected identifier")

        if (builder.getTokenType.equals(ScalaTokenTypes.tLOWER_BOUND)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLOWER_BOUND)
          Type.parse(builder)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tUPPER_BOUND)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tUPPER_BOUND)
          Type.parse(builder)
        }

        if (builder.getTokenType.equals(ScalaTokenTypes.tVIEW)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tVIEW)
          Type.parse(builder)
        }

        typeParamClause.done(ScalaElementTypes.TYPE_PARAM)
      }
    }


    object ClassParamClauses extends Constr{
      override def parse(builder : PsiBuilder) : Unit = {
        val classParamClausesMarker = builder.mark()
        var chooseParsingWay = builder.mark()

        var first = builder.getTokenType()
        builder.advanceLexer
        Console.println("first in class param clause " + first)

        var second = builder.getTokenType()
        builder.advanceLexer
        Console.println("second in class param clause " + second)

        var third = builder.getTokenType()
        builder.advanceLexer
        Console.println("third in class param clause " + third)

        while (checkForClassParamClause(first, second, third)) {
          chooseParsingWay.rollbackTo()
          ClassParamClause.parse(builder)
          chooseParsingWay = builder.mark()

          first = builder.getTokenType()
          builder.advanceLexer
          second = builder.getTokenType()
          builder.advanceLexer
          third = builder.getTokenType()
        }


        if (checkForImplicit(first, second, third)) {
          chooseParsingWay.rollbackTo()
          Console.println("check for implicit")
          ImplicitEnd.parse(builder)
        }

        chooseParsingWay.rollbackTo()
        //chooseParsingWay.drop()

        classParamClausesMarker.done(ScalaElementTypes.CLASS_PARAM_CLAUSES)
      }
    }

    object ClassParamClause extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val classParamClauseMarker = builder.mark()
        if (builder.getTokenType().equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
        }

        if (builder.getTokenType().equals(ScalaTokenTypes.tLPARENTHIS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
        } else builder.error("expected '('")

        builder.getTokenType() match {
          case ScalaTokenTypes.kVAL
             | ScalaTokenTypes.kVAR
             | ScalaTokenTypes.kABSTRACT
             | ScalaTokenTypes.kFINAL
             | ScalaTokenTypes.kOVERRIDE
             | ScalaTokenTypes.kPRIVATE
             | ScalaTokenTypes.kPROTECTED
             | ScalaTokenTypes.kSEALED
             | ScalaTokenTypes.tIDENTIFIER
           => {
            ClassParams.parse(builder)
          }

          case _ => {}
        }

        if (builder.getTokenType().equals(ScalaTokenTypes.tRPARENTHIS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
        } else builder.error("expected ')'")

        classParamClauseMarker.done(ScalaElementTypes.CLASS_PARAM_CLAUSE)
      }
    }

    object ImplicitEnd extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
         if (builder.getTokenType().equals(ScalaTokenTypes.tLINE_TERMINATOR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
        }

        if (builder.getTokenType().equals(ScalaTokenTypes.tLPARENTHIS)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLPARENTHIS)
        } else builder.error("expected '('")

        builder.getTokenType() match {
          case ScalaTokenTypes.kIMPLICIT => {
            ClassParams.parse(builder)
          }

          case _ => {}
        }


        if (builder.getTokenType().equals(ScalaTokenTypes.tRPARENTHIS)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)
        } else builder.error("expected ')'")
      }
    }

    object ClassParams extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val classParamsMarker = builder.mark()
        builder.getTokenType() match {
          case ScalaTokenTypes.kVAL
             | ScalaTokenTypes.kVAR
             | ScalaTokenTypes.kABSTRACT
             | ScalaTokenTypes.kFINAL
             | ScalaTokenTypes.kOVERRIDE
             | ScalaTokenTypes.kPRIVATE
             | ScalaTokenTypes.kPROTECTED
             | ScalaTokenTypes.kSEALED
             | ScalaTokenTypes.tIDENTIFIER
           => {
            ClassParam.parse(builder)
          }

          case _ => { builder.error("wrong declaration")}
        }

        while (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

          builder.getTokenType() match {
          case ScalaTokenTypes.kVAL
             | ScalaTokenTypes.kVAR
             | ScalaTokenTypes.kABSTRACT
             | ScalaTokenTypes.kFINAL
             | ScalaTokenTypes.kOVERRIDE
             | ScalaTokenTypes.kPRIVATE
             | ScalaTokenTypes.kPROTECTED
             | ScalaTokenTypes.kSEALED
             | ScalaTokenTypes.tIDENTIFIER
           => {
            ClassParam.parse(builder)
          }
        }
      }

      classParamsMarker.done(ScalaElementTypes.CLASS_PARAMS)
    }

    object ClassParam extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val classParamMarker = builder.mark()

        if (BNF.firstModifier.contains(builder.getTokenType)) {
          builder.getTokenType() match {
            case ScalaTokenTypes.kABSTRACT => { ParserUtils.eatElement(builder, ScalaTokenTypes.kABSTRACT) }
            case ScalaTokenTypes.kFINAL => { ParserUtils.eatElement(builder, ScalaTokenTypes.kFINAL) }
            case ScalaTokenTypes.kOVERRIDE => { ParserUtils.eatElement(builder, ScalaTokenTypes.kOVERRIDE) }
            case ScalaTokenTypes.kPRIVATE => { ParserUtils.eatElement(builder, ScalaTokenTypes.kPRIVATE) }
            case ScalaTokenTypes.kPROTECTED => { ParserUtils.eatElement(builder, ScalaTokenTypes.kPROTECTED) }
            case ScalaTokenTypes.kSEALED => { ParserUtils.eatElement(builder, ScalaTokenTypes.kSEALED) }
          }

          builder.getTokenType() match {
            case ScalaTokenTypes.kVAL => { ParserUtils.eatElement(builder, ScalaTokenTypes.kVAL) }
            case ScalaTokenTypes.kVAR => { ParserUtils.eatElement(builder, ScalaTokenTypes.kVAR) }
            case _ => { builder.error("expected 'val' or 'var'") }
          }

        }

        if (builder.getTokenType().equals(ScalaTokenTypes.tIDENTIFIER)) {
          Param.parse(builder)
        } else builder.error("expected identifier")

        classParamMarker.done(ScalaElementTypes.CLASS_PARAM)
      }
    }
  }



    object Param extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val paramsMarker = builder.mark()

        if (builder.getTokenType().equals(ScalaTokenTypes.tIDENTIFIER)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        } else builder.error("expected identifier")

        if (builder.getTokenType().equals(ScalaTokenTypes.tCOLON)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)
        } else builder.error("expected ':'")

        builder.getTokenType() match {
          case ScalaTokenTypes.tIDENTIFIER
             | ScalaTokenTypes.kTHIS
             | ScalaTokenTypes.kSUPER
             | ScalaTokenTypes.tLPARENTHIS
             | ScalaTokenTypes.tFUNTYPE
             => {
             ParamType.parse(builder)
          }
        }

       paramsMarker.done(ScalaElementTypes.PARAM)
      }
    }

    object ParamType extends Constr {
      override def parse(builder : PsiBuilder) : Unit = {
        val paramTypeMarker = builder.mark()

        if (builder.getTokenType().equals(ScalaTokenTypes.tFUNTYPE)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)
        }

        builder.getTokenType() match {
          case ScalaTokenTypes.tIDENTIFIER
             | ScalaTokenTypes.kTHIS
             | ScalaTokenTypes.kSUPER
             | ScalaTokenTypes.tLPARENTHIS
             => {
             Type.parse(builder)
          }
        }

        if (builder.getTokenType().equals(ScalaTokenTypes.tSTAR)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tSTAR)
        }

        paramTypeMarker.done(ScalaElementTypes.PARAM_TYPE)
      }
    }

  case class TraitDef extends TypeDef {
    def getKeyword = ScalaTokenTypes.kTRAIT

    def getDef = ScalaElementTypes.TRAIT_DEF

    def parseDef ( builder : PsiBuilder ) : Unit = {

      while ( !builder.getTokenType.equals(ScalaTokenTypes.tLBRACE) ){
        builder.advanceLexer
      }
    }
  }

  override def parse(builder : PsiBuilder) : Unit = {
    def parseInst ( builder : PsiBuilder ) : Unit = {
        Console.println("expected class or object : " + builder.getTokenType())
        builder.getTokenType() match {
          case ScalaTokenTypes.kCLASS => {
            ClassDef.parse(builder)
          }

          case ScalaTokenTypes.kOBJECT => {
              ObjectDef.parse(builder)
          }
        }
    }

    Console.println("token type : " + builder.getTokenType())
    builder.getTokenType() match {
      case ScalaTokenTypes.kCASE => {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kCASE)

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

