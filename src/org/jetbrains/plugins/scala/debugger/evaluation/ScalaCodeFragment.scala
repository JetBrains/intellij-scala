package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.openapi.command.undo.{BasicUndoableAction, UndoManager}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.IntentionFilterOwner.IntentionActionsFilter
import com.intellij.psi.JavaCodeFragment.{ExceptionHandler, VisibilityChecker}
import com.intellij.psi._
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}

import scala.collection.mutable.HashSet

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCodeFragment(project: Project, text: String) extends {
  private var vFile = new LightVirtualFile("Dummy.scala",
    ScalaFileType.SCALA_FILE_TYPE, text)
  private var provider = new SingleRootFileViewProvider(
    PsiManager.getInstance(project), vFile, true)
} with ScalaFileImpl(provider) with JavaCodeFragment {
  getViewProvider.forceCachedPsi(this)

  override def getViewProvider = provider

  private var thisType: PsiType = null
  private var superType: PsiType = null
  private var exceptionHandler: ExceptionHandler = null
  private var resolveScope: GlobalSearchScope = null
  private var filter: IntentionActionsFilter = null
  private var imports: HashSet[String] = new HashSet

  def getThisType: PsiType = thisType

  def setThisType(psiType: PsiType) {thisType = psiType}

  def getSuperType: PsiType = superType

  def setSuperType(superType: PsiType) {this.superType = superType}

  def importsToString(): String = {
    imports.mkString(",")
  }

  def addImportsFromString(imports: String) {
    this.imports ++= imports.split(',')
  }

  def setVisibilityChecker(checker: VisibilityChecker) {}

  def getVisibilityChecker: VisibilityChecker = VisibilityChecker.EVERYTHING_VISIBLE

  def setExceptionHandler(checker: ExceptionHandler) {exceptionHandler = checker}

  def getExceptionHandler: ExceptionHandler = exceptionHandler

  def forceResolveScope(scope: GlobalSearchScope) {resolveScope = scope}

  def getForcedResolveScope: GlobalSearchScope = resolveScope

  def setIntentionActionsFilter(filter: IntentionActionsFilter) {this.filter = filter}

  def getIntentionActionsFilter: IntentionActionsFilter = filter

  override def isScriptFile: Boolean = false

  override def isScriptFile(withCashing: Boolean): Boolean = false

  override def addImportForPath(path: String, ref: PsiElement, explicitly: Boolean) {
    imports += path
    myManager.beforeChange(false)
    val project: Project = myManager.getProject
    val document: Document = PsiDocumentManager.getInstance(project).getDocument(this)
    UndoManager.getInstance(project).undoableActionPerformed(
      new ScalaCodeFragment.ImportClassUndoableAction(path, document, imports)
    )
    //todo: that's a hack, how to remove it?..
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
    document.insertString(0, "a")
    document.deleteString(0, 1)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    for (qName <- imports) {
      val imp = ScalaPsiElementFactory.createImportFromTextWithContext("import _root_." + qName, this, this)
      if (!imp.processDeclarations(processor, state, lastParent, place)) return false
    }
    if (!super.processDeclarations(processor, state, lastParent, place)) return false
    true
  }

  override def clone(): PsiFileImpl = {
    val clone = cloneImpl(calcTreeElement.clone.asInstanceOf[FileElement]).asInstanceOf[ScalaCodeFragment]
    clone.imports = this.imports
    clone.vFile = new LightVirtualFile("Dummy.scala",
      ScalaFileType.SCALA_FILE_TYPE, getText)
    clone.provider = new SingleRootFileViewProvider(
      PsiManager.getInstance(project), clone.vFile, true)
    clone.provider.forceCachedPsi(clone)
    clone
  }
}

object ScalaCodeFragment {
  private class ImportClassUndoableAction(path: String, document: Document,
                                          imports: HashSet[String]) extends BasicUndoableAction {
    def undo() {
      imports -= path
    }
    def redo() {
      imports += path
    }
  }

}