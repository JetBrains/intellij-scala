package org.jetbrains.plugins.scala.lang.parser.parsing


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.PsiBuilder

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.10.2006
 * Time: 12:53:26
 */

class Program extends ScalaTokenTypes {
    def parse(builder: PsiBuilder): Unit = {
        var marker = builder.mark()
        while( !builder.eof() ){
            (new ListOfExpression()).parse(builder);
        }
        marker.done(ScalaElementTypes.FILE)
    }
}