package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  *         Time: 9:23:53
  */
trait ScTemplateParents extends ScalaPsiElement {

  def typeElements: collection.Seq[ScTypeElement]

  def superTypes: collection.Seq[ScType]

  def allTypeElements: collection.Seq[ScTypeElement]

  final def constructorInvocation: Option[ScConstructorInvocation] = findChild(classOf[ScConstructorInvocation])

  final def typeElementsWithoutConstructor: collection.Seq[ScTypeElement] =
    findChildrenByClassScala(classOf[ScTypeElement])
}
