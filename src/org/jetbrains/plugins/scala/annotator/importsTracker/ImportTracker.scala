package org.jetbrains.plugins.scala.annotator.importsTracker


import collection.mutable._
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.ImportUsed

/**
 * @author Ilyas
 */

class ImportTracker {
  private val usedImports: Map[ScalaFile, Set[ImportUsed]] = new HashMap[ScalaFile, Set[ImportUsed]]
  private val unusedImports: Map[ScalaFile, Set[ImportUsed]] = new HashMap[ScalaFile, Set[ImportUsed]]

  def registerUsedImports(file: ScalaFile, used: Set[ImportUsed]) {
    usedImports.get(file) match {
      case None => usedImports += Tuple(file, used)
      case Some(set: Set[ImportUsed]) => set ++= used
    }
  }

  private val lock = new Object()
  def getUnusedImport(file: ScalaFile): Set[ImportUsed] = {
    lock synchronized {
      def foo = {
        val res = file.getAllImportUsed
        usedImports.get(file) match {
          case Some(used: Set[ImportUsed]) => {
            res --= used
          }
          case _ =>
        }
        unusedImports += Tuple(file, res)
        usedImports -= file
        res
      }
      unusedImports.getOrElse(file, foo)
    }
  }

  def removeAnnotatedFile(file: ScalaFile) {
    lock synchronized {
      unusedImports -= file
    }
  }
}

object ImportTracker {
  def getInstance(project: Project): ImportTracker = {
    ServiceManager.getService(project, classOf[ImportTracker])
  }
}