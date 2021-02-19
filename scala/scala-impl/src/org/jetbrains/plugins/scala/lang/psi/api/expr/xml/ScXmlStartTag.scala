package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr
package xml

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
* Date: 21.04.2008
*/

trait ScXmlStartTagBase extends ScXmlPairedTagBase { this: ScXmlStartTag =>
  def getClosingTag: ScXmlEndTag = {
    if (getParent != null && getParent.getLastChild.isInstanceOf[ScXmlEndTag]) {
      return getParent.getLastChild.asInstanceOf[ScXmlEndTag]
    }
    null
  }

  override def getMatchedTag: ScXmlPairedTag = getClosingTag
}