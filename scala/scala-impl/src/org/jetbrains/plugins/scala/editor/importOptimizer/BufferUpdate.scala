package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.util.diff.Diff

import scala.collection.JavaConverters._

object BufferUpdate {

  def updateIncrementally[B, Elem, T <: AnyRef](buffer: B, finalResult: Array[Elem])
                                               (compareBy: Elem => T)
                                               (implicit operations: BufferOperations[B, Elem]): Unit = {

    val changes = Diff.buildChanges[T](operations.asArray(buffer).map(compareBy), finalResult.map(compareBy))
    if (changes == null) {
      return
    }

    for (change <- changes.toList.asScala.reverse) {
      if (change.deleted > 0) {
        operations.remove(buffer, change.line0, change.deleted)
      }
      if (change.inserted > 0) {
        val toInsert =
          finalResult.slice(change.line1, change.line1 + change.inserted).toSeq
        operations.insert(buffer, change.line0, toInsert)
      }
    }
  }
}



