package org.jetbrains.plugins.scala.lang.parser;
/**
* @author ilyas 
*/

import org.jetbrains.plugins.scala.lang.parser.util._
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import ScalaElementTypes._

trait ParserNode extends ScalaTokenTypes{

  def lookAhead(builder: PsiBuilder, elems: IElementType*) = ParserUtils.lookAhead(builder, elems: _*)

}