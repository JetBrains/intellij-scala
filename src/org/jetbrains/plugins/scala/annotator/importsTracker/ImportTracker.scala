package org.jetbrains.plugins.scala.annotator.importsTracker


import _root_.scala.collection.mutable.HashMap
import _root_.scala.collection.Set
import _root_.scala.collection.Map
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.imports.usages.ImportUsed

/**
 * @author Ilyas
 */

class ImportTracker {
  //todo: remove fields, use putUserData instead
  private val usedImports: HashMap[ScalaFile, collection.mutable.Set[ImportUsed]] = new HashMap[ScalaFile, collection.mutable.Set[ImportUsed]]
  private val unusedImports: HashMap[ScalaFile, collection.mutable.Set[ImportUsed]] = new HashMap[ScalaFile, collection.mutable.Set[ImportUsed]]

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

  private val lock = new Object()

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