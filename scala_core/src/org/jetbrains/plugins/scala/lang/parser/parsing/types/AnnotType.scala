package org.jetbrains.plugins.scala.lang.parser.parsing.types

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.02.2008
*/

/*
 * AnnotType ::= {Annotation} SimpleType
 */

object AnnotType {
  def parse(builder: PsiBuilder): Boolean = {
    val annotMarker = builder.mark
    val annotationsMarker = builder.mark
    var isAnnotation = false
    while (Annotation.parse(builder)) {isAnnotation = true}
    if (isAnnotation) annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    else annotationsMarker.drop
    //parse Simple type
    if (SimpleType parse builder){
      if (isAnnotation) annotMarker.done(ScalaElementTypes.ANNOT_TYPE)
      else annotMarker.drop
      return true
    }
    else {
      annotMarker.rollbackTo
      return false
    }
  }
}