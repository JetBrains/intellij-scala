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
object Stable {
             /*
  def parse(builder : PsiBuilder, marker : PsiBuilder.Marker ) : Unit = {

         builder.getTokenType match {
           case ScalaTokenTypes.tDOT => {

             val preMarker = marker.precede()

             marker.done( ScalaTokenTypes.tIDENTIFIER )

            //dot node
             val dotMarker = builder.mark();
             builder.advanceLexer // Ate dot

             dotMarker.done( ScalaTokenTypes.tDOT )


             builder.getTokenType match {
               case ScalaTokenTypes.tIDENTIFIER => {

                //identifier node
                 val idMarker = builder.mark()
                 builder.advanceLexer

                 idMarker.done(ScalaTokenTypes.tIDENTIFIER)

                 (new StableId).parse(builder, preMarker)
               }

               case ScalaTokenTypes.tUNDER => {

                 val underMarker = builder.mark()
                 builder.advanceLexer

                 underMarker.done(ScalaTokenTypes.tUNDER)

                 preMarker.done(ScalaElementTypes.STABLE_ID)
               }
              
               case _ => {
                  builder.error("Wrong import ")
                  preMarker.done(ScalaElementTypes.STABLE_ID)
               }

             }
           }

           case ScalaTokenTypes.tLINE_TERMINATOR => {
             marker.done(ScalaElementTypes.STABLE_ID)
           }

           case ScalaTokenTypes.tSEMICOLON => {
              marker.done(ScalaElementTypes.STABLE_ID)
           }

           case ScalaTokenTypes.tCOMMA => {
              marker.done(ScalaElementTypes.STABLE_ID)
          }

           case _ => {
            marker.drop()
            builder.error("Wrong import : StableID")
           }
         }

    }
    */
  }