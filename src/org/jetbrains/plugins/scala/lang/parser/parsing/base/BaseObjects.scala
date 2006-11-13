package org.jetbrains.plugins.scala.lang.parser.parsing.base {

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableIdInImport
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Exprs
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
/**
 * User: Dmitry.Krasilschikov
 * Date: 17.10.2006
 * Time: 11:38:56
 */

/*
    StatementSeparator ::= NewLine | ‘;’
*/

object StatementSeparator extends ConstrWithoutNode {
  //override def getElementType = ScalaElementTypes.STATEMENT_SEPARATOR

  override def parseBody(builder: PsiBuilder): Unit = {
    Console.println("token type : " + builder.getTokenType())
     builder.getTokenType() match {
      case ScalaTokenTypes.tSEMICOLON => {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tSEMICOLON)
      }

      case ScalaTokenTypes.tLINE_TERMINATOR => {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
      }

      case _ => { builder.error("wrong statement separator")}
    }
  }
}

/*
    AttributeClause ::= ‘[’ Attribute {‘,’ Attribute} ‘]’ [NewLine]
*/

object AttributeClause extends ConstrItem {
  override def first : TokenSet = TokenSet.create (
    Array (
      ScalaTokenTypes.tLSQBRACKET
    )
  )

  override def getElementType = ScalaElementTypes.ATTRIBUTE_CLAUSE;

  override def parseBody(builder: PsiBuilder): Unit = {

    Console.println("token type : " + builder.getTokenType())
        builder.getTokenType() match {
      //expected left square brace
      case ScalaTokenTypes.tLSQBRACKET => {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)

        Attribute.parse(builder)

        //possible attributes, separated by comma
        while (builder.getTokenType().equals(ScalaTokenTypes.tCOMMA)){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
          Console.println("Attribute parse")

          Attribute.parse(builder)
          Console.println("Attribute parsed")
        }

        //expected right square brace
        if (builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
        } else {
          builder.error("expected ']'")
        }

        Console.println("token type : " + builder.getTokenType())
        builder.getTokenType() match {
          //possible line terminator
          case ScalaTokenTypes.tLINE_TERMINATOR => {
            ParserUtils.eatElement(builder, ScalaTokenTypes.tLINE_TERMINATOR)
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
  override def getElementType = ScalaElementTypes.ATTRIBUTE

  override def parseBody(builder: PsiBuilder): Unit = {
    Construction.parse(builder)
  }
}

/*
    Constr ::= StableId [TypeArgs] {‘(’ [Exprs] ‘)’}
*/

object Construction extends Constr{
  override def getElementType = ScalaElementTypes.CONSTRUCTION
  
  override def parseBody(builder: PsiBuilder): Unit = {
    Console.println("token type : " + builder.getTokenType())
    builder.getTokenType() match {
      case ScalaTokenTypes.tIDENTIFIER => {
        //todo
        StableId.parse(builder)

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
            while (builder.getTokenType().equals(ScalaTokenTypes.tLPARENTHIS)) {
               Console.println("expr in parenthis parse")
                ExprInParenthis.parse(builder)
                Console.println("expr in parenthis parsed")
             
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

  object ExprInParenthis {

    def parse(builder: PsiBuilder): Unit = {

      Console.println("token type in expr in par: " + builder.getTokenType())
      builder.getTokenType() match {
        case ScalaTokenTypes.tLPARENTHIS => {
          val lparenthisMarker = builder.mark()
          builder.advanceLexer
          lparenthisMarker.done(ScalaTokenTypes.tLPARENTHIS)

          Console.println("token type in expr in par 2: " + builder.getTokenType())
          if (BNF.firstExpr.contains(builder.getTokenType)) {
            Exprs.parse(builder)
          } else builder.error("expected expression")


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
    override def getElementType = ScalaElementTypes.TYPE_ARGS

    override def parseBody(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())

      if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tLSQBRACKET)

        Types.parse(builder)

        if (builder.getTokenType().equals(ScalaTokenTypes.tRSQBRACKET)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
        } else {
          builder.error("expected ']'")
        }
      }
    }
  }

/*
    types :== Type {',' Type}
*/
  
  object Types extends Constr{
    override def getElementType = ScalaElementTypes.TYPES
    override def parseBody(builder: PsiBuilder): Unit = {
      if (BNF.firstType.contains(builder.getTokenType)){
        Type parse builder

        while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

          Console.println("possible type parse")
          if (BNF.firstType.contains(builder.getTokenType)) {
            Type parse builder
          } else builder error "expected identifier"
        }
      }
    }
  }



/*
    Modifier ::= LocalModifier
    | override
    | private [ "[" id "]" ]
    | protected [ "[" id "]" ]
*/

  object Modifier extends Constr {
    override def getElementType = ScalaElementTypes.MODIFIER

    override def parseBody(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())
      if (BNF.firstLocalModifier.contains(builder.getTokenType)) {
        LocalModifier.parse(builder)
      }

      if (ScalaTokenTypes.kOVERRIDE.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.kOVERRIDE)
      }

      if (BNF.firstAccessModifier.contains(builder.getTokenType)) {
        AccessModifier.parse(builder)
      }
    }
  }

  object AccessModifier extends ConstrWithoutNode {
    //override def getElementType = ScalaElementTypes.ACCESS_MODIFIER

    override def parseBody(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())

      builder.getTokenType match {
        case ScalaTokenTypes.kPRIVATE => ParserUtils.eatElement(builder, ScalaTokenTypes.kPRIVATE)
        case ScalaTokenTypes.kPROTECTED => ParserUtils.eatElement(builder, ScalaTokenTypes.kPROTECTED)
      }

      if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)){
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
        } else builder.error("expected identifier")

        if ( builder.getTokenType.equals(ScalaTokenTypes.tRSQBRACKET) ){
          ParserUtils.eatElement(builder, ScalaTokenTypes.tRSQBRACKET)
        } else builder.error("expected ']'")
      }
    }
  }


  object LocalModifier extends ConstrWithoutNode {
    override def parseBody(builder: PsiBuilder): Unit = {
      Console.println("token type : " + builder.getTokenType())

      if (BNF.firstLocalModifier.contains(builder.getTokenType)) {
        builder.getTokenType() match {
          case ScalaTokenTypes.kABSTRACT => ParserUtils.eatElement(builder, ScalaTokenTypes.kABSTRACT)

          case ScalaTokenTypes.kFINAL => ParserUtils.eatElement(builder, ScalaTokenTypes.kFINAL)

          case ScalaTokenTypes.kSEALED => ParserUtils.eatElement(builder, ScalaTokenTypes.kSEALED)

          case ScalaTokenTypes.kIMPLICIT => ParserUtils.eatElement(builder, ScalaTokenTypes.kIMPLICIT)

          case _ => builder error "expected local modifier"
        }

      } else builder error "expected local modifier"

    }
  }

  object Import extends Constr {
    override def getElementType = ScalaElementTypes.IMPORT_STMT

    override def parseBody(builder: PsiBuilder): Unit = {
      builder.getTokenType() match {
        case ScalaTokenTypes.kIMPORT => {
          ParserUtils.eatElement(builder, ScalaTokenTypes.kIMPORT)

          /*builder.getTokenType() match {
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

          } */

          ParserUtils.listOfSmth(builder, ImportExpr, ScalaTokenTypes.tCOMMA, ScalaElementTypes.IMPORT_EXPRS)
        }

        case _ => { builder.error("expected 'import'") }
      }
    }


  object ImportExpr extends ConstrItem {
    override def getElementType = ScalaElementTypes.IMPORT_EXPR

    override def first = TokenSet.create (
      Array(
        ScalaTokenTypes.tIDENTIFIER
      )
    )

    override def parseBody(builder: PsiBuilder): Unit = {

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

    }
  }

  object ImportSelectors extends Constr{
    override def getElementType = ScalaElementTypes.IMPORT_SELECTORS

    override def parseBody(builder: PsiBuilder): Unit = {

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

          //todo: import selectors are cyclic
          while (checkForImportSelectors(first, second, third, fourth)) {
          Console.println("ImportSel  parse")
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

    }
  }

  object ImportSelector extends Constr{
    override def getElementType = ScalaElementTypes.IMPORT_SELECTOR

    override def parseBody(builder: PsiBuilder): Unit = {
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
    }
  }
 }

 object Ids extends Constr {
  def getElementType : IElementType = ScalaElementTypes.IDENTIFIER_LIST

    override def parse(builder : PsiBuilder) : Unit = {
      val marker = builder.mark()
      parseBody(builder)
      marker.done(getElementType)
    }

    def parseBody(builder : PsiBuilder) : Unit = {

     if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
      ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
     } else builder error "expected identifier"

     while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
       ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)
       Console.println("Ids parse")

       if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
         ParserUtils.eatElement(builder, ScalaTokenTypes.tIDENTIFIER)
       } else builder error "expected identifier"
     }
   }
 }

}
