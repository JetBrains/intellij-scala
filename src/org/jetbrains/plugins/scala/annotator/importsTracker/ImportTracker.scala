package org.jetbrains.plugins.scala
package annotator
package importsTracker


import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed}

import scala.collection.{Set, mutable}

/**
 * @author Alexander Podkhalyuzin
 */
class ImportTracker {
  def registerUsedImports(file: ScalaFile, used: Set[ImportUsed]) {
    val refHolder = ScalaRefCountHolder.getInstance(file)
    for (imp <- used) {
      refHolder.registerImportUsed(imp)
    }
  }

  def getUnusedImport(file: ScalaFile): Seq[ImportUsed] = {
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

object ImportTracker {
  def getInstance(project: Project): ImportTracker = {
    ServiceManager.getService(project, classOf[ImportTracker])
  }
}
