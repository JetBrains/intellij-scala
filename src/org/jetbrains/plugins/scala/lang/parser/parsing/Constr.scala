package org.jetbrains.plugins.scala.lang.parser.parsing

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType;
/**
 * User: Dmitry.Krasilschikov
 * Date: 19.10.2006
 * Time: 17:35:24
 */
abstract class Constr {
   def parse (builder : PsiBuilder ) : Unit = {
     val marker = builder.mark()
     parseBody(builder)
     marker.done(getElementType)
   }

   def getElementType : IElementType
   def parseBody (builder : PsiBuilder) : Unit

}
