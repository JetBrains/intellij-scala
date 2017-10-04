package org.jetbrains.plugins.scala.annotator.importsTracker

import java.util.concurrent.atomic.AtomicReference

import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.project.UserDataHolderExt
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import scala.collection.mutable

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.05.2010
 */

/**
 * See com.intellij.codeInsight.daemon.impl.RefCountHolder
 */
class ScalaRefCountHolder private () {
  private final val myState: AtomicReference[Integer] = new AtomicReference[Integer](State.VIRGIN)
  private object State {val VIRGIN = 0; val WRITE = 1; val READY = 2; val READ = 3;}
  private val myImportUsed = ContainerUtil.newConcurrentSet[ImportUsed]()
  private val myValueUsed = ContainerUtil.newConcurrentSet[ValueUsed]()

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
    !myImportUsed.contains(used)
  }

  def isValueWriteUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    myValueUsed.contains(WriteValueUsed(e))
  }

  def isValueReadUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    myValueUsed.contains(ReadValueUsed(e))
  }

  def isValueUsed(e: PsiNamedElement): Boolean = {
    assertIsRetrieving()
    isValueReadUsed(e) || isValueWriteUsed(e)
  }

  private def removeInvalidRefs() {
    assertIsAnalyzing()
    myImportUsed synchronized {
      val iterator: java.util.Iterator[ImportUsed] = myImportUsed.iterator
      while (iterator.hasNext) {
        val ref: ImportUsed = iterator.next
        if (!ref.e.isValid) {
          iterator.remove()
        }
      }
    }
    myValueUsed synchronized {
      val valuesIterator: java.util.Iterator[ValueUsed] = myValueUsed.iterator()
      while (valuesIterator.hasNext) {
        val ref: ValueUsed = valuesIterator.next
        if (!ref.isValid) valuesIterator.remove()
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
    true
  }


  def retrieveUnusedReferencesInfo(analyze: () => Unit): Boolean = {
    if (!myState.compareAndSet(State.READY, State.READ)) {
      return false
    }
    try {
      analyze()
    }
    finally {
      val set: Boolean = myState.compareAndSet(State.READ, State.READY)
      assert(set, myState.get)
    }
    true
  }


  private def assertIsAnalyzing() {
    assert(myState.get == State.WRITE, myState.get)
  }


  private def assertIsRetrieving() {
    assert(myState.get == State.READ, myState.get)
  }
}

object ScalaRefCountHolder {
  private val SCALA_REF_COUNT_HOLDER_IN_FILE_KEY: Key[ScalaRefCountHolder] = Key.create("scala.ref.count.holder.in.file.key")

  def getInstance(file: PsiFile): ScalaRefCountHolder = {
    val myFile = Option(ScalaLanguageDerivative.getScalaFileOnDerivative(file)).getOrElse(file)
    myFile.getOrUpdateUserData(SCALA_REF_COUNT_HOLDER_IN_FILE_KEY, new ScalaRefCountHolder)
  }
}