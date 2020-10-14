package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.collection.immutable.ArraySeq

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  *         Time: 9:23:53
  */
trait ScTemplateParents extends ScalaPsiElement {

  def typeElements: Seq[ScTypeElement]

  def superTypes: Seq[ScType]

  def allTypeElements: Seq[ScTypeElement]

  final def constructorInvocation: Option[ScConstructorInvocation] = findChild(classOf[ScConstructorInvocation])

  final def typeElementsWithoutConstructor: Seq[ScTypeElement] =
    ArraySeq.unsafeWrapArray(findChildrenByClassScala(classOf[ScTypeElement]))
}
