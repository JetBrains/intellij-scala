package org.jetbrains.plugins.scala.lang.parser.parsing.base {

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.parsing
import org.jetbrains.plugins.scala.lang.parser.parsing.top.Package
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableIdInImport
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Exprs
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
/**
 * User: Dmitry.Krasilschikov
 * Date: 17.10.2006
 * Time: 11:38:56
 */

/*
    StatementSeparator ::= NewLine | ‘;’
*/

object StatementSeparator extends Constr{
  override def parse(builder: PsiBuilder): Unit = {

    Console.println("token type : " + builder.getTokenType())
     builder.getTokenType() match {
      case ScalaTokenTypes.tSEMICOLON => {
        val semicolonMarker = builder.mark()
        builder.advanceLexer

        semicolonMarker.done(ScalaTokenTypes.tSEMICOLON)
      }

      case ScalaTokenTypes.tLINE_TERMINATOR => {
        val lineTerminatorMarker = builder.mark()
        builder.advanceLexer

        lineTerminatorMarker.done(ScalaTokenTypes.tLINE_TERMINATOR)
      }

      case _ => { builder.error("wrong statement separator")}
    }

  }
}

/*
    AttributeClause ::= ‘[’ Attribute {‘,’ Attribute} ‘]’ [NewLine]
*/

object AttributeClause extends Constr{
  override def parse(builder: PsiBuilder): Unit = {

    Console.println("token type : " + builder.getTokenType())
        builder.getTokenType() match {
      //expected left square brace
      case ScalaTokenTypes.tLSQBRACKET => {
        val lsqbracketMarker = builder.mark()
        builder.advanceLexer
        lsqbracketMarker.done(ScalaTokenTypes.tLSQBRACKET)

        val attributeMarker = builder.mark()
        Attribute.parse(builder)
        attributeMarker.done(ScalaElementTypes.ATTRIBUTE)

        //possible attributes, separated by comma
        while (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)){
          val commaMarker = builder.mark()
          builder.advanceLexer
          commaMarker.done(ScalaTokenTypes.tCOMMA)

          val attributeMarker = builder.mark()
          Attribute.parse(builder)
          attributeMarker.done(ScalaElementTypes.ATTRIBUTE)
        }


        //expected right square brace
        if (builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
          val rsqbracketMarker = builder.mark()
          builder.advanceLexer
          rsqbracketMarker.done(ScalaTokenTypes.tRSQBRACKET)

        } else {
          builder.error("expected ']'")
        }

        Console.println("token type : " + builder.getTokenType())
        builder.getTokenType() match {
          //possible line terminator
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            val lineTerminatorMarker = builder.mark()
            builder.advanceLexer
            lineTerminatorMarker.done(ScalaTokenTypes.tLINE_TERMINATOR)
          }

          case _ => {}
        }
      }

      case _ => { builder.error("wrong statement separator")}
    }

  }

}

/*
    Attribute ::= Constr
*/

object Attribute extends Constr{
  override def parse(builder: PsiBuilder): Unit = {
    Construction.parse(builder)
  }
}

/*
    Constr ::= StableId [TypeArgs] {‘(’ [Exprs] ‘)’}
*/

object Construction extends Constr{
  override def parse(builder: PsiBuilder): Unit = {
    Console.println("token type : " + builder.getTokenType())
    builder.getTokenType() match {
      case ScalaTokenTypes.tIDENTIFIER => {
        //val stableIdMarker = builder.mark()

        //parse stable identifier
        //todo
        StableId.parse(builder)

        //stableIdMarker.done(ScalaElementTypes.STABLE_ID)

        Console.println("token type : " + builder.getTokenType())
        builder.getTokenType() match {
          case ScalaTokenTypes.tLSQBRACKET => {
            val typeArgsMarker = builder.mark()

            TypeArgs.parse(builder)

            typeArgsMarker.done(ScalaElementTypes.TYPE_ARGS)

            //expect right closing bracket
            if (!builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
              builder.error("epected ']'")
            }

          }

          case ScalaTokenTypes.tLPARENTHIS => {
             //possible left parenthis - begining of list epression
            while (builder.getTokenType().equals(ScalaTokenTypes.tLPARENTHIS)) {

                ExprInParenthis.parse(builder)
             
            }
          }

          case _ => {}
        }
      }

      case _ => {builder.error("expected identifier")}
    }
  }

/*
    ExprInParenthis :== '(' [exprs] ')'
*/

  object ExprInParenthis extends Constr {
    override def parse(builder: PsiBuilder): Unit = {

      Console.println("token type in expr in par: " + builder.getTokenType())
      builder.getTokenType() match {
        case ScalaTokenTypes.tLPARENTHIS => {
          val lparenthisMarker = builder.mark()
          builder.advanceLexer
          lparenthisMarker.done(ScalaTokenTypes.tLPARENTHIS)

          Console.println("token type in expr in par 2: " + builder.getTokenType())
          builder.getTokenType() match {
            case ScalaTokenTypes.tINTEGER
               | ScalaTokenTypes.tFLOAT
               | ScalaTokenTypes.kTRUE
               | ScalaTokenTypes.kFALSE
               | ScalaTokenTypes.tCHAR
               | ScalaTokenTypes.kNULL
               | ScalaTokenTypes.tSTRING_BEGIN
               | ScalaTokenTypes.tPLUS
               | ScalaTokenTypes.tMINUS
               | ScalaTokenTypes.tTILDA
               | ScalaTokenTypes.tNOT
               | ScalaTokenTypes.tIDENTIFIER
               => {
               val exprsMarker = builder.mark()

               //parse expression list
               val res = Exprs.parse(builder)

               Console.println("res exprs" + res.toString())

               exprsMarker.done(ScalaElementTypes.EXPRS)
            }

            case _ => { builder.error("expected expression") }
          }

          if (builder.getTokenType().equals(ScalaTokenTypes.tRPARENTHIS)) {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tRPARENTHIS)

          } else {
            builder.error("expected ')'")
          }
        }
      }

    }
  }

 }

 /*
    TypeArgs :== '[' Types']'
 */

  object TypeArgs extends Constr{

    override def parse(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())
      builder.getTokenType() match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val lsqbracketMarker = builder.mark()
          builder.advanceLexer
          lsqbracketMarker.done(ScalaTokenTypes.tLSQBRACKET)

          val typesMarker = builder.mark()
          Types.parse(builder)
          typesMarker.done(ScalaElementTypes.TYPE_ARGS)

          if (builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
            val rsqbracketMarker = builder.mark()
            builder.advanceLexer
            rsqbracketMarker.done(ScalaTokenTypes.tRSQBRACKET)
          } else {
            builder.error("expected ']'")
          }

        }

      }
    }
  }

/*
    types :== Type {',' Type}
*/
  
  object Types extends Constr{
    override def parse(builder: PsiBuilder): Unit = {

    }
  }



/*
    Modifier ::= LocalModifier
    | override
    | private [ "[" id "]" ]
    | protected [ "[" id "]" ]
*/

  object Modifier extends Constr{
    override def parse(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())
      builder.getTokenType() match {
         case ScalaTokenTypes.kABSTRACT
            | ScalaTokenTypes.kFINAL
            | ScalaTokenTypes.kSEALED
            | ScalaTokenTypes.kIMPLICIT
            => {
            val localModifierMarker = builder.mark()
            LocalModifier.parse(builder)
            localModifierMarker.done(ScalaElementTypes.LOCAL_MODIFIER)
         }

        case ScalaTokenTypes.kOVERRIDE => {
          val overrideMarker = builder.mark()
          builder.advanceLexer
          overrideMarker.done(ScalaTokenTypes.kOVERRIDE)
        }

        case ScalaTokenTypes.kPRIVATE
           | ScalaTokenTypes.kPROTECTED
           => {
           val accessModifierMarker = builder.mark()
           AccessModifier.parse(builder)
           accessModifierMarker.done(ScalaElementTypes.MODIFIER_ACCESS)
        }
      }
    }
  }

  object AccessModifier extends Constr{
    override def parse(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())
      builder.getTokenType() match {
        case ScalaTokenTypes.tLSQBRACKET => {
          val lsqbracketMarker = builder.mark()
          builder.advanceLexer
          lsqbracketMarker.done(ScalaTokenTypes.tLSQBRACKET)

          Console.println("token type : " + builder.getTokenType())
          builder.getTokenType() match {
            case ScalaTokenTypes.tIDENTIFIER => {
              val idMarker = builder.mark()
              builder.advanceLexer
              idMarker.done(ScalaTokenTypes.tIDENTIFIER)
            }

            case _ => { builder.error("expected identifier") }
          }

          if ( !builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET) ){
            builder.error("expected ']'")
          }
        }
      }
    }
  }


  object LocalModifier extends Constr{
    override def parse(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())
      builder.getTokenType() match {
        case ScalaTokenTypes.kABSTRACT
           | ScalaTokenTypes.kFINAL
           | ScalaTokenTypes.kSEALED
           | ScalaTokenTypes.kIMPLICIT
           => {
             val localModifierMarker = builder.mark()
             builder.advanceLexer
             localModifierMarker.done(ScalaElementTypes.LOCAL_MODIFIER)
           }
       }
    }
  }

  object Import extends Constr {
    override def parse(builder: PsiBuilder): Unit = {

      builder.getTokenType() match {
        case ScalaTokenTypes.kIMPORT => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kIMPORT)

          builder.getTokenType() match {
            case ScalaTokenTypes.tIDENTIFIER => {
              val importExprsMarker = builder.mark()

              ImportExpr.parse(builder)
              while (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)){
                ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
                Console.println("comma in importExpr")
                ImportExpr.parse(builder)
              }

              importExprsMarker.done(ScalaElementTypes.IMPORT_EXPRS)
            }
            case _ => { builder.error("expected identifier") }

          }
        }

        case _ => { builder.error("expected 'import'") }
      }
    }


  object ImportExpr extends Constr {
    override def parse(builder: PsiBuilder): Unit = {
      val importExprMarker = builder.mark()

      builder.getTokenType() match {
        case ScalaTokenTypes.tIDENTIFIER => {
          val stableIdMarker = builder.mark()
          StableIdInImport.parse(builder)
         // StableId.parse(builder)

          stableIdMarker.done(ScalaElementTypes.STABLE_ID)

          Console.println("expect '.' " + builder.getTokenType())

          if (builder.getTokenType().equals(ScalaTokenTypes.tDOT)) {
            Console.println("ate dot " + builder.getTokenType)
            ParserUtils.eatElement(builder, ScalaTokenTypes.tDOT)

            Console.println("after dot " + builder.getTokenType)
            builder.getTokenType() match {
              case ScalaTokenTypes.tIDENTIFIER => {
                Console.println("identifier " + builder.getTokenText())
                ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
              }

              case ScalaTokenTypes.tUNDER => {
              Console.println("under " + builder.getTokenText())
                ParserUtils.eatElement(builder, ScalaTokenTypes.tUNDER)
              }

              case ScalaTokenTypes.tLBRACE => {
                Console.println("import selectors handle")

                ImportSelectors.parse(builder)

                Console.println("import selectors handled")
              }

              case _ => { builder.error("expected '.'") }
            }

          } else {
            builder.error("expected '.'")
          }
        }

        case _ => { builder.error("expected identifier") }
      }

      importExprMarker.done(ScalaElementTypes.IMPORT_EXPR)
    }
  }

  object ImportSelectors extends Constr{
    override def parse(builder: PsiBuilder): Unit = {

     def checkForImportSelectors (first : IElementType, second : IElementType, third : IElementType, fourth : IElementType) : Boolean = {
      if (!first.equals(ScalaTokenTypes.tIDENTIFIER))
        return false

      if (second.equals(ScalaTokenTypes.tCOMMA))
        return true

       if (second.equals(ScalaTokenTypes.tFUNTYPE))
         if (third.equals(ScalaTokenTypes.tIDENTIFIER) || third.equals(ScalaTokenTypes.tUNDER))
           if (fourth.equals(ScalaTokenTypes.tCOMMA))
             return true

       return false
    }

      var importSelectorsMarker = builder.mark()

      builder.getTokenType() match {
        case ScalaTokenTypes.tLBRACE => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tLBRACE)

          val chooseImportSelectorsWay = builder.mark()
          val first =  builder.getTokenType; builder.advanceLexer
          val second = builder.getTokenType; builder.advanceLexer
          val third = builder.getTokenType; builder.advanceLexer
          val fourth = builder.getTokenType; builder.advanceLexer

          chooseImportSelectorsWay.rollbackTo()
          val importSelectorsMarker = builder.mark()
          var isImportSelectors : Boolean = false

          while (checkForImportSelectors(first, second, third, fourth)) {
            isImportSelectors = true

            if (isImportSelectors) {
              ImportSelector.parse(builder)

              if (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)) {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
              } else builder.error("expected ','")
            }
          }

          if (isImportSelectors) importSelectorsMarker.done(ScalaElementTypes.IMPORT_SELECTOR_LIST)
          else importSelectorsMarker.drop()

          builder.getTokenType() match {
            case ScalaTokenTypes.tIDENTIFIER => {
              ImportSelector.parse(builder)
            }

            case ScalaTokenTypes.tUNDER => {
              ParserUtils.eatElement(builder, ScalaTokenTypes.tUNDER)
            }

            case _ => { builder.error("expected identifier or '_'")}
          }

          if ( !builder.getTokenType().equals(ScalaTokenTypes.tRBRACE) )
            builder.error("expected '}'")
        }

        case _ => { builder.error("expected '{'")}
      }

      importSelectorsMarker.done(ScalaElementTypes.IMPORT_SELECTORS)
    }
  }

  object ImportSelector extends Constr{
    override def parse(builder: PsiBuilder): Unit = {
      val importSelectorMarker = builder.mark()

      builder.getTokenType() match {
        case ScalaTokenTypes.tIDENTIFIER => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)

        builder.getTokenType() match {
          case ScalaTokenTypes.tFUNTYPE => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tFUNTYPE)

            builder.getTokenType() match {
              case ScalaTokenTypes.tIDENTIFIER => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
              }

              case ScalaTokenTypes.tUNDER => {
                ParserUtils.eatElement(builder, ScalaTokenTypes.tUNDER)
              }

              case _ => { builder.error("expected identifier or '_'") }
            }
  
          }

          case _ => {}
        }

        }

        case _ => { builder.error("expected identifier") }
      }

      importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTOR)
    }
  }
 }

}
