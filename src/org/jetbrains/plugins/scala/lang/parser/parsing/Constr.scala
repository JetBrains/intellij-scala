package org.jetbrains.plugins.scala.lang.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree._
import com.intellij.psi.tree.TokenSet
/**
 * User: Dmitry.Krasilschikov
 * Date: 19.10.2006
 * Time: 17:35:24
 */

/*
 * Construction is a node in PSI tree; type of node added from getElementType method
 */

abstract class Constr {
   def parse (builder : PsiBuilder ) : Unit = {
     if (!builder.eof()) {
       val marker = builder.mark()
       parseBody(builder)
       marker.done(getElementType)
     } else builder error "unexpected end of file"  
   }

   def getElementType : IElementType
   def parseBody (builder : PsiBuilder) : Unit

}

trait Item {
   def first : TokenSet
}

trait ConstrItem extends Constr with Item {
}
 
/*
 * Construction is a node in PSI tree; type defines in parseBody method
 */

abstract class ConstrUnpredict {
  def parse (builder : PsiBuilder ) : Unit = {
    if (!builder.eof()) {
       parseBody(builder)
    } else builder error "unexpected end of file"
  }

   def parseBody (builder : PsiBuilder) : Unit
}

/*
 * Construction isn't a node; parseNode method returned type of node
 */

abstract class ConstrReturned {
  def parse (builder : PsiBuilder ) : IElementType = {
    if (!builder.eof()) {
      parseBody(builder)
    } else {
      builder error "unexpected end of file"
      ScalaElementTypes.WRONGWAY
    }
  }

   def parseBody (builder : PsiBuilder) : IElementType
}

/*
 * Construction without a node
 */

abstract class ConstrWithoutNode {
  def parse (builder : PsiBuilder ) : Unit = {
   if (!builder.eof()) {
     parseBody(builder)
   } else builder error "unexpected end of file"
  }

  def parseBody (builder : PsiBuilder) : Unit
}