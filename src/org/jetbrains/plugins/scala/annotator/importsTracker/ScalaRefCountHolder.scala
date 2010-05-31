package org.jetbrains.plugins.scala.annotator.importsTracker

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import java.util.concurrent.atomic.AtomicReference
import com.intellij.util.containers.ConcurrentHashSet
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import com.intellij.openapi.util.{TextRange, Key, UserDataHolderEx}
import com.intellij.psi._

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

/**
 * See com.intellij.codeInsight.daemon.impl.RefCountHolder
 */
class ScalaRefCountHolder private (file: PsiFile) {
  private final val myState: AtomicReference[Integer] = new AtomicReference[Integer](State.VIRGIN)
  private object State {val VIRGIN = 0; val WRITE = 1; val READY = 2; val READ = 3;}
  private val myImportUsed = new ConcurrentHashSet[ImportUsed]

  private def clear: Unit = {
    assertIsAnalyzing
    myImportUsed.clear
  }

  def registerImportUsed(used: ImportUsed): Unit = {
    myImportUsed.add(used)
  }

  def isRedundant(used: ImportUsed): Boolean = {
    assertIsRetrieving
    return !myImportUsed.contains(used)
  }

  private def removeInvalidRefs: Unit = {
    assertIsAnalyzing
    var iterator: java.util.Iterator[ImportUsed] = myImportUsed.iterator
    while (iterator.hasNext) {
      var ref: ImportUsed = iterator.next
      if (!ref.e.isValid) {
        iterator.remove
      }
    }
  }

  def analyze(analyze: Runnable, dirtyScope: TextRange, file: PsiFile): Boolean = {
    myState.compareAndSet(State.READY, State.VIRGIN)
    if (!myState.compareAndSet(State.VIRGIN, State.WRITE)) return false
    try {
      if (dirtyScope != null) {
        if (dirtyScope.equals(file.getTextRange)) {
          clear
        }
        else {
          removeInvalidRefs
        }
      }
      analyze.run
    }
    finally {
      var set: Boolean = myState.compareAndSet(State.WRITE, State.READY)
      assert(set, myState.get)
    }
    return true
  }


  def retrieveUnusedReferencesInfo(analyze: Runnable): Boolean = {
    if (!myState.compareAndSet(State.READY, State.READ)) {
      return false
    }
    try {
      analyze.run
    }
    finally {
      var set: Boolean = myState.compareAndSet(State.READ, State.READY)
      assert (set, myState.get)
    }
    return true
  }


  private def assertIsAnalyzing: Unit = {
    assert (myState.get == State.WRITE, myState.get)
  }


  private def assertIsRetrieving: Unit = {
    assert (myState.get == State.READ, myState.get)
  }
}

object ScalaRefCountHolder {
  val SCALA_REF_COUNT_HOLDER_IN_FILE_KEY: Key[ScalaRefCountHolder] = Key.create("scala.ref.count.holder.in.file.key")

  def getInstance(file: PsiFile): ScalaRefCountHolder = {
    var refCountHolder: ScalaRefCountHolder = file.getUserData(SCALA_REF_COUNT_HOLDER_IN_FILE_KEY)
    if (refCountHolder == null) {
      refCountHolder = (file.asInstanceOf[UserDataHolderEx]).putUserDataIfAbsent(SCALA_REF_COUNT_HOLDER_IN_FILE_KEY,
        new ScalaRefCountHolder(file))
    }
    return refCountHolder
  }
}