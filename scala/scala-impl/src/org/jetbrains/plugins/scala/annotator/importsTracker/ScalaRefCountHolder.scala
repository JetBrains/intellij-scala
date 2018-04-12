package org.jetbrains.plugins.scala.annotator.importsTracker

import java.util.concurrent.atomic.AtomicReference

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.editor.event.{EditorFactoryEvent, EditorFactoryListener}
import com.intellij.openapi.editor.{Document, Editor, EditorFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{LowMemoryWatcher, TextRange}
import com.intellij.psi._
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.util.ScalaLanguageDerivative

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

  def noUsagesFound(used: ImportUsed): Boolean = {
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

  def getDirtyScope(file: PsiFile): Option[TextRange] = {
    val project = file.getProject
    val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)

    if (document == null) Some(file.getTextRange)
    else DaemonCodeAnalyzer.getInstance(project) match {
      case analyzerImpl: DaemonCodeAnalyzerImpl =>
        val fileStatusMap = analyzerImpl.getFileStatusMap
        Option(fileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL))
      case _ => Some(file.getTextRange)
    }
  }

  def getInstance(file: PsiFile): ScalaRefCountHolder = {
    val myFile = Option(ScalaLanguageDerivative.getScalaFileOnDerivative(file)).getOrElse(file)
    val component = myFile.getProject.getComponent(classOf[ScalaRefCountHolderComponent])
    component.getOrCreate(myFile, new ScalaRefCountHolder)
  }
}

class ScalaRefCountHolderComponent(project: Project) extends AbstractProjectComponent(project) {

  override def projectOpened(): Unit = {

    LowMemoryWatcher.register(() => refCountHolders.clear(), project)

    EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryListener {
      override def editorCreated(event: EditorFactoryEvent): Unit = {}
      override def editorReleased(event: EditorFactoryEvent): Unit = removeHoldersWithNoEditor(event.getEditor)
    }, project)
  }

  override def projectClosed(): Unit = {
    refCountHolders.clear()
  }

  def getOrCreate(file: PsiFile, holder: => ScalaRefCountHolder): ScalaRefCountHolder = refCountHolders.synchronized {
    refCountHolders.computeIfAbsent(file, _ => holder)
  }

  private[this] val refCountHolders = ContainerUtil.createWeakMap[PsiFile, ScalaRefCountHolder]()

  private[this] def hasNoDocumentOrEditor(file: PsiFile, releasedEditor: Editor): Boolean = {
    val project = file.getProject
    val document = PsiDocumentManager.getInstance(project).getCachedDocument(file)

    document == null || !hasOtherEditors(document, releasedEditor)
  }

  private[this] def hasOtherEditors(document: Document, releasedEditor: Editor): Boolean = {
    EditorFactory.getInstance().getEditors(document, project) match {
      case Array() | Array(`releasedEditor`) => false
      case _ => true
    }
  }

  private[this] def removeHoldersWithNoEditor(releasedEditor: Editor): Unit = {
    refCountHolders.synchronized {
      val files = refCountHolders.keySet()
      files.removeIf(f => hasNoDocumentOrEditor(f, releasedEditor))
    }
  }
}