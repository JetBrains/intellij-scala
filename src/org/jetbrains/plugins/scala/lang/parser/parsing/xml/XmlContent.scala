package org.jetbrains.plugins.scala.lang.parser.parsing.xml

import com.intellij.lang.PsiBuilder
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

