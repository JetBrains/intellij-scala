package org.jetbrains.plugins.scala
package annotator
package importsTracker


import collection.Set
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.ImportUsed
import com.intellij.codeInsight.daemon.impl.RefCountHolder
import java.lang.String
import com.intellij.psi._
import impl.light.LightElement
import collection.mutable.{ArrayBuffer, HashMap}

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
    val refHolder = ScalaRefCountHolder.getInstance(file)
    val runnable = new Runnable {
      def run {
        while (!iter.isEmpty) {
          val used = iter.next
          if (refHolder.isRedundant(used)) buff += used
        }
      }
    }
    refHolder.retrieveUnusedReferencesInfo(runnable)
    return buff.toArray
  }
}

object ImportTracker {
  def getInstance(project: Project): ImportTracker = {
    ServiceManager.getService(project, classOf[ImportTracker])
  }
}