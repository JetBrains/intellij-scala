package org.jetbrains.plugins.scala.lang.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet
/**
 * User: Dmitry.Krasilschikov
 * Date: 19.10.2006
 * Time: 17:35:24
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

trait ConstrItemReturned extends ConstrReturned with Item {
}

abstract class ConstrUnpredict {
  def parse (builder : PsiBuilder ) : Unit = {
     parseBody(builder)
   } 

   def parseBody (builder : PsiBuilder) : Unit
}

abstract class ConstrReturned {
  def parse (builder : PsiBuilder ) : IElementType = {
     parseBody(builder)
   }

   def parseBody (builder : PsiBuilder) : IElementType
}

abstract class ConstrWithoutNode {
  def parse (builder : PsiBuilder ) : Unit = {
     parseBody(builder)
   }

   def parseBody (builder : PsiBuilder) : Unit
}