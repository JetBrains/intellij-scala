package org.jetbrains.plugins.scala.lang.parser.parsing.top;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.lang.parser.parsing.top.ListOfStableIDs
/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 22:13:17
 */
class StableId {

  def parse(builder : PsiBuilder, marker : PsiBuilder.Marker ) : Unit = {

    Console.println("       token in stableID : " + builder.getTokenType)

         builder.getTokenType match {
           case ScalaTokenTypes.tDOT => {

             Console.println("      precede do ")
             val preMarker = marker.precede()

             Console.println("      idMarker done ")
             marker.done( ScalaElementTypes.IDENTIFIER )

            //dot node
            Console.println("        dotMarker do ")
             val dotMarker = builder.mark();
             builder.advanceLexer // Ate dot

             Console.println("        dotMarker done ")
             dotMarker.done( ScalaTokenTypes.tDOT )


             builder.getTokenType match {
               case ScalaTokenTypes.tIDENTIFIER => {

                Console.println("        idMarker do ")
                //identifier node
                 val idMarker = builder.mark()
                 builder.advanceLexer

                 idMarker.done(ScalaElementTypes.IDENTIFIER)
                 Console.println("        idMarker done ")

                 (new StableId).parse(builder, preMarker)
               }

               case ScalaTokenTypes.tUNDER => {

                Console.println("        underMarker do ")

                 val underMarker = builder.mark()
                 builder.advanceLexer

                 underMarker.done(ScalaTokenTypes.tUNDER)
                 Console.println("        underMarker done ")

                 preMarker.done(ScalaElementTypes.STABLE_ID)
               }
              
               case _ => {
                  builder.error("Wrong import ")
                  preMarker.done(ScalaElementTypes.STABLE_ID)
               }

             }
           }

           case ScalaTokenTypes.tWHITE_SPACE_LINE_TERMINATE => {
             Console.println("        marker in whsps will be do ")
             marker.done(ScalaElementTypes.STABLE_ID)
             Console.println("        marker in whsps done ")
             //builder.advanceLexer
           }

           case ScalaTokenTypes.tSEMICOLON => {

              marker.done(ScalaElementTypes.STABLE_ID)
              Console.println("       marker in semicolon done ")
              
             /* val semiMarker = builder.mark()
              //builder.advanceLexer
              semiMarker.done(ScalaElementTypes.SEMICOLON)
            */
             // builder.advanceLexer
              //new Import() parse(builder)
           }

           case ScalaTokenTypes.tCOMMA => {
              marker.done(ScalaElementTypes.STABLE_ID)

            /*  val commaMarker = builder.mark()
              commaMarker.done( ScalaTokenTypes.tCOMMA ) //new node: COMMA
              */
//              val marker = builder.mark()

             // (new ListOfStableIDs).parse(builder)
          }

           case _ => {
            marker.drop()
            builder.error("Wrong import : StableID")
           }
         }

    }
  }