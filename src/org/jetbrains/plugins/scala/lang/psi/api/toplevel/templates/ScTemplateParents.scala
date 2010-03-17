package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import impl.ScalaPsiElementFactory
import psi.ScalaPsiElement
import base.types.ScTypeElement
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:23:53
*/

trait ScTemplateParents extends ScalaPsiElement {
  def typeElements: Seq[ScTypeElement] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScTypeElement]).toSeq :_*)
  def superTypes: Seq[ScType]
}