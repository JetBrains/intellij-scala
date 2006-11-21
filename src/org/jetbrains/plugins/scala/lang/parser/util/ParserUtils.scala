package org.jetbrains.plugins.scala.lang.parser.util

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.ConstrItem
import org.jetbrains.plugins.scala.lang.parser.parsing.Constr
import com.intellij.psi.tree.IElementType

import com.intellij.lang.PsiBuilder

object ParserUtils {

  def rollForward(builder: PsiBuilder) : Boolean = {

    var counter = 0
    while (!builder.eof()){
       builder.getTokenType match{
         case ScalaTokenTypes.tLINE_TERMINATOR => {
           builder.advanceLexer
           counter=counter+1
         }
         case _ => return (counter == 0)
       }
    }
    counter == 0
  }

  //Write element node
  def eatElement(builder: PsiBuilder, elem: IElementType): Unit = {
    if (!builder.eof()) {
      if (elem.equals(ScalaTokenTypes.tIDENTIFIER)){
        val marker = builder.mark()
        builder.advanceLexer // Ate something
        marker.done(elem)
        //rollForward(builder)
      } else {
        builder.advanceLexer
        //rollForward(builder)
      }
    } else {
      builder error "unexpected end of file"
      false
    }
  }

  /*def listOfSmthWithoutNode(builder: PsiBuilder, itemType : ConstrItem, delimiter : IElementType) : Unit = {

    if (itemType.first.contains(builder.getTokenType)) {
      itemType parse builder
    } else {
      builder error "wrong item"
      return
    }

    while (!builder.eof() && builder.getTokenType.equals(delimiter)) {
      eatElement(builder, delimiter);

      if (itemType.first.contains(builder.getTokenType)) {
        itemType parse builder
      } else {
        builder error "expected next item"
        return
      }
    }
  } */

  def listOfSmth(builder: PsiBuilder, itemType : ConstrItem, delimiter : IElementType, listType : IElementType) : Unit = {
    val listMarker = builder.mark()

    if (itemType.first.contains(builder.getTokenType)) {
      itemType parse builder
    } else {
      builder error "wrong item"
      listMarker.drop
      return
    }

    var numberOfElements = 1;
    while (!builder.eof() && builder.getTokenType.equals(delimiter)) {
      eatElement(builder, delimiter);

      if (itemType.first.contains(builder.getTokenType)) {
        itemType parse builder
        numberOfElements + 1
      } else {
        builder error "expected next item"
        listMarker.drop
        return
      }
    }

    if (numberOfElements > 1) listMarker.done(listType)
    else listMarker.drop
  }

  /*
  def listOfSmth(builder: PsiBuilder, itemType : ConstrItem, delimiter : IElementType, listType : IElementType) : Unit = {

    val listMarker = builder.mark()

    listOfSmthWithoutNode(builder, itemType, delimiter)

    listMarker.done(listType)
  }
  */

  /*
   *   Parse block in breaces
   *
   *   smthInBraces parse block between braces : expected pair '{ }', '[ ]', '{ }'
   *   @param builder - PsiBuilder of parser
   *   @param constr - The construction in block
   *   @param leftBrace - The left brace of block
   *   @param rightBrace - The right brace of block
   *   @param blockType - The type of block
   *
   *   @return parsed block
   */

  def smthInBraces(builder: PsiBuilder, constr : Constr, leftBrace : IElementType, rightBrace : IElementType, blockType : IElementType) : IElementType= {
    val blockMarker = builder.mark()

    if (leftBrace.equals(builder.getTokenType)) {
      eatElement(builder, leftBrace)

      constr parse builder

      if (rightBrace.equals(builder.getTokenType)) {
        eatElement(builder, rightBrace)
      } else {
        builder error "expected right brace"
        return ScalaElementTypes.WRONGWAY
      }

    } else {
      builder error "expected left brace"
      return ScalaElementTypes.WRONGWAY
    }

    blockMarker.done(blockType)
    blockType
  }

  def eatConstr(builder : PsiBuilder, constr: Constr, element : IElementType) : IElementType = {
    val marker = builder.mark()
    //Console.println("before parsing " + constr + " : " + builder.getTokenType())
    constr.parse(builder)
    //Console.println("after parsing " + constr + " : " + builder.getTokenType())
    marker.done(element)
    element
  }
  

  //Write element node
  def errorToken(builder: PsiBuilder,
                 marker: PsiBuilder.Marker ,
                 msg: String,
                 elem: ScalaElementType): ScalaElementType = {
    builder.error(msg)
    //marker.done(elem)
    marker.rollbackTo()
    ScalaElementTypes.WRONGWAY
  }


}