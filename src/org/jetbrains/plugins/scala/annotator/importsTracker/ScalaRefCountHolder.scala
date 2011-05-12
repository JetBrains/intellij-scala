package org.jetbrains.plugins.scala.annotator.importsTracker

import java.util.concurrent.atomic.AtomicReference
import com.intellij.openapi.util.{TextRange, Key, UserDataHolderEx}
import com.intellij.psi._
import java.lang.Integer
import com.intellij.util.containers.ConcurrentHashSet
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ReadValueUsed, WriteValueUsed, ValueUsed, ImportUsed}

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
  private val myValueUsed = new ConcurrentHashSet[ValueUsed]()

  private def clear() {
    assertIsAnalyzing()
    myImportUsed.clear()
    myValueUsed.clear()
  }

  def registerImportUsed(used: ImportUsed) {
    myImportUsed.add(used)
  }

  def registerValueUsed(used: ValueUsed) {
    myValueUsed.add(used)
  }

  def isRedundant(used: ImportUsed): Boolean = {
    assertIsRetrieving()
    return !myImportUsed.contains(used)
  }

  def isValueWriteUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    return myValueUsed.contains(WriteValueUsed(e))
  }

  def isValueReadUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    return myValueUsed.contains(ReadValueUsed(e))
  }

  def isValueUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    return isValueReadUsed(e) || isValueWriteUsed(e)
  }

  private def removeInvalidRefs() {
    assertIsAnalyzing()
    val iterator: java.util.Iterator[ImportUsed] = myImportUsed.iterator
    while (iterator.hasNext) {
      val ref: ImportUsed = iterator.next
      if (!ref.e.isValid) {
        iterator.remove()
      }
    }
    val valuesIterator: java.util.Iterator[ValueUsed] = myValueUsed.iterator()
    while (valuesIterator.hasNext) {
      val ref: ValueUsed = valuesIterator.next
      if (!ref.e.isValid) {
        iterator.remove()
      }
    }
  }

  def analyze(analyze: Runnable, dirtyScope: TextRange, file: PsiFile): Boolean = {
    myState.compareAndSet(State.READY, State.VIRGIN)
    if (!myState.compareAndSet(State.VIRGIN, State.WRITE)) return false
    try {
      if (dirtyScope != null) {
        if (dirtyScope.equals(file.getTextRange)) {
          clear()
        }
        else {
          removeInvalidRefs()
        }
      }
      analyze.run()
    }
    finally {
      val set: Boolean = myState.compareAndSet(State.WRITE, State.READY)
      assert(set, myState.get)
    }
    return true
  }


  def retrieveUnusedReferencesInfo(analyze: Runnable): Boolean = {
    if (!myState.compareAndSet(State.READY, State.READ)) {
      return false
    }
    try {
      analyze.run()
    }
    finally {
      val set: Boolean = myState.compareAndSet(State.READ, State.READY)
      assert(set, myState.get)
    }
    return true
  }


  private def assertIsAnalyzing() {
    assert(myState.get == State.WRITE, myState.get)
  }


  private def assertIsRetrieving() {
    assert(myState.get == State.READ, myState.get)
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