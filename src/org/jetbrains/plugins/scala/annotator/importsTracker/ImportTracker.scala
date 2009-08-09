package org.jetbrains.plugins.scala
package annotator
package importsTracker


import collection.mutable.HashMap
import collection.Set
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.ImportUsed

/**
 * @author Ilyas, Alexander Podkhalyuzin
 */

class ImportTracker {
  //todo: remove fields, use putUserData instead
  private val usedImports: HashMap[ScalaFile, collection.mutable.Set[ImportUsed]] = new HashMap[ScalaFile, collection.mutable.Set[ImportUsed]]
  private val unusedImports: HashMap[ScalaFile, collection.mutable.Set[ImportUsed]] = new HashMap[ScalaFile, collection.mutable.Set[ImportUsed]]
  private val annotatedFiles: HashMap[ScalaFile, Boolean] = new HashMap[ScalaFile, Boolean]
  private val lock = new Object()

  def registerUsedImports(file: ScalaFile, used: Set[ImportUsed]) {
    lock synchronized {
      usedImports.get(file) match {
        case None => {
          val res = new collection.mutable.HashSet[ImportUsed]()
          res ++= used.iterator
          usedImports += Tuple(file, res)  // todo BUG I_VAR!!!
        }
        case Some(set: collection.mutable.Set[ImportUsed]) => set ++= used
      }
    }
  }

  def getUnusedImport(file: ScalaFile): Set[ImportUsed] = {
    lock synchronized {
      def foo = {
        val res = new collection.mutable.HashSet[ImportUsed]()
        res ++= file.getAllImportUsed.iterator
        usedImports.get(file) match {
          case Some(used: Set[ImportUsed]) => {
            res --= used.iterator
          }
          case _ =>
        }
        unusedImports += Tuple(file, res)
        usedImports -= file
        res
      }
      if (!isFileAnnotated(file)) return collection.mutable.HashSet.empty
      unusedImports.getOrElse(file, foo)
    }
  }

  def removeAnnotatedFile(file: ScalaFile) {
    lock synchronized {
      unusedImports -= file
      annotatedFiles.put(file, false)
    }
  }

  def markFileAnnotated(file: ScalaFile) {
    lock synchronized {
      annotatedFiles.put(file, true)
    }
  }

  def isFileAnnotated(file: ScalaFile): Boolean = {
    lock synchronized {
      annotatedFiles.getOrElse(file, false)
    }
  }
}

object ImportTracker {
  def getInstance(project: Project): ImportTracker = {
    ServiceManager.getService(project, classOf[ImportTracker])
  }
}