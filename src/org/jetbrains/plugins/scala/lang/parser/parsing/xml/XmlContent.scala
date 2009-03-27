package org.jetbrains.plugins.scala.lang.parser.parsing.xml

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import com.intellij.psi.xml.XmlTokenType

/**
* @author Alexander Podkhalyuzin
* Date: 18.04.2008
*/

/*
 * XmlContent ::= Element
 *              | CDSect
 *              | PI
 *              | Comment
 */

object XmlContent {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case XmlTokenType.XML_START_TAG_START => {
        Element parse builder
      }
      case XmlTokenType.XML_COMMENT_START => {
        Comment parse builder
      }
      case XmlTokenType.XML_CDATA_START => {
        CDSect parse builder
      }
      case XmlTokenType.XML_PI_START => {
        PI parse builder
      }
      case _ => false
    }
  }
}

