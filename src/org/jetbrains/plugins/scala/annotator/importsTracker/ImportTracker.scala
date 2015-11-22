package org.jetbrains.plugins.scala
package annotator
package importsTracker


import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed

import scala.collection.Set
import scala.collection.mutable.ArrayBuffer

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

  def getUnusedImport(file: ScalaFile): Array[ImportUsed] = {
    val buff = new ArrayBuffer[ImportUsed]
    val iter = file.getAllImportUsed.iterator
    val refHolder = ScalaRefCountHolder getInstance file
    val runnable = new Runnable {
      def run() {
        while (iter.nonEmpty) {
          val used = iter.next()
          if (refHolder.isRedundant(used)) buff += used
        }
      }
    }
    
    refHolder retrieveUnusedReferencesInfo runnable
    buff.toArray
  }
}

object ImportTracker {
  def getInstance(project: Project): ImportTracker = {
    ServiceManager.getService(project, classOf[ImportTracker])
  }
}