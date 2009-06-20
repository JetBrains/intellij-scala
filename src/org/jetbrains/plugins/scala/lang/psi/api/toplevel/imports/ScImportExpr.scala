package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

trait ScImportExpr extends ScalaPsiElement {
  def reference: Option[ScStableCodeReferenceElement] = findChild(classOf[ScStableCodeReferenceElement])

  def selectorSet: Option[ScImportSelectors]

  def selectors: Seq[ScImportSelector] = {
    selectorSet match {
      case None => Seq.empty
      case Some(s) => s.selectors
    }
  }

  def singleWildcard: Boolean

  def wildcardElement: Option[PsiElement]

  def qualifier: ScStableCodeReferenceElement

  def deleteExpr: Unit

  def getNames: Array[String] = getLastChild match {
    case s: ScImportSelectors => (for (selector <- selectors) yield selector.getText).toArray
    case _ => getNode.getLastChildNode.getText match {
      case "_" => Array[String]("_")
      case _ if getNode.getLastChildNode.getLastChildNode != null => Array[String](getNode.getLastChildNode.getLastChildNode.getText)
      case _ => Array[String]()
    }

  }
}