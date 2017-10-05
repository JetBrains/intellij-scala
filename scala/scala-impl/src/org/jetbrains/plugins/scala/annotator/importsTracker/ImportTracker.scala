package org.jetbrains.plugins.scala
package annotator
package importsTracker


import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.collection.mutable
import scala.collection.Set

/**
 * @author Alexander Podkhalyuzin
 */
object ImportTracker {

  def registerUsedImports(elem: PsiElement, imports: Set[ImportUsed]): Unit = {
    if (!elem.isValid) return

    elem.getContainingFile match {
      case scalaFile: ScalaFile =>
        val refHolder = ScalaRefCountHolder.getInstance(scalaFile)
        imports.foreach(refHolder.registerImportUsed)
      case _ =>
    }
  }

  def registerUsedImports(element: PsiElement, resolveResult: ScalaResolveResult): Unit = {
    registerUsedImports(element, resolveResult.importsUsed)
  }

  def getUnusedImports(file: ScalaFile): Seq[ImportUsed] = {
    val buff = new mutable.HashSet[ImportUsed]()
    val imports = file.getAllImportUsed
    val refHolder = ScalaRefCountHolder.getInstance(file)

    refHolder.retrieveUnusedReferencesInfo { () =>
      imports.foreach {
        case used@ImportSelectorUsed(e) => //if the entire line is unused, highlight the entire line
          if (refHolder.isRedundant(used)) {
            e.parent.flatMap(_.parent) match {
              case Some(expr: ScImportExpr) if expr.selectors.map(ImportSelectorUsed).forall(refHolder.isRedundant) =>
                buff += ImportExprUsed(expr)
              case _ => buff += used
            }
          }
        case used  =>
          if (refHolder.isRedundant(used)) {
            buff += used
          }
      }
    }
    buff.toSeq
  }
}
