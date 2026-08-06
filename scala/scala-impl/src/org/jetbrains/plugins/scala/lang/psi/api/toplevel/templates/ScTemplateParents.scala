package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package templates

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScTemplateParents extends ScalaPsiElement {

  def typeElements: Seq[ScTypeElement]

  def superTypes: Seq[ScType]

  def supersText: String

  def allTypeElements: Seq[ScTypeElement]

  final def firstParentClause: Option[ScConstructorInvocation] = findChild[ScConstructorInvocation]

  final def parentClauses: Seq[ScConstructorInvocation] = findChildren[ScConstructorInvocation]

  final def typeElementsWithoutConstructor: Seq[ScTypeElement] = parentClauses.collect {
    case inv: ScConstructorInvocation if inv.args.isEmpty => inv.typeElement
  }
}
