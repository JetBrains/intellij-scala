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

trait ScXmlEndTagBase extends ScXmlPairedTagBase { this: ScXmlEndTag =>
  def getOpeningTag: ScXmlStartTag = {
    if (getParent != null && getParent.getFirstChild.isInstanceOf[ScXmlStartTag]) {
      return getParent.getFirstChild.asInstanceOf[ScXmlStartTag]
    }
    null
  }

  override def getMatchedTag: ScXmlPairedTag = getOpeningTag
}