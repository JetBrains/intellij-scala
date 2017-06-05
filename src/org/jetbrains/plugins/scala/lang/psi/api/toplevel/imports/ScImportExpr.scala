package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

trait ScImportExpr extends ScalaPsiElement {
  def reference: Option[ScStableCodeReferenceElement]

  def selectorSet: Option[ScImportSelectors]

  def selectors: Seq[ScImportSelector] = selectorSet.toSeq.flatMap {
    _.selectors.toSeq
  }

  def isSingleWildcard: Boolean

  def wildcardElement: Option[PsiElement]

  def qualifier: ScStableCodeReferenceElement

  def deleteExpr()

  def importedNames: Seq[String] = selectorSet match {
    case Some(set) => set.selectors.flatMap(_.importedName)
    case _ if isSingleWildcard => Seq("_")
    case _ => reference.toSeq.map(_.refName)
  }

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitImportExpr(this)
}
