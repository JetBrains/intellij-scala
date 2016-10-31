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

  def getNames: Array[String] = getLastChild match {
    case _: ScImportSelectors => (for (selector <- selectors) yield selector.getText).toArray
    case _ => getNode.getLastChildNode.getText match {
      case "_" => Array[String]("_")
      case _ if getNode.getLastChildNode.getLastChildNode != null => Array[String](getNode.getLastChildNode.getLastChildNode.getText)
      case _ => Array[String]()
    }
  }

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitImportExpr(this)
}
