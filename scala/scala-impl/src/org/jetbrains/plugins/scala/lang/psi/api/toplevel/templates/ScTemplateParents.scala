package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  *         Time: 9:23:53
  */
trait ScTemplateParents extends ScalaPsiElement {

  def typeElements: Seq[ScTypeElement]

  def superTypes: Seq[ScType]

  def allTypeElements: Seq[ScTypeElement]

  final def constructor: Option[ScConstructor] = findChild(classOf[ScConstructor])

  final def typeElementsWithoutConstructor: Seq[ScTypeElement] =
    findChildrenByClassScala(classOf[ScTypeElement])

  override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case visitor: ScalaElementVisitor => visitor.visitTemplateParents(this)
    case _ => visitor.visitElement(this)
  }
}
