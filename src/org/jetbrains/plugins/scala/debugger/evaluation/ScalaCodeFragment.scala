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
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}

import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  */

class ScalaCodeFragment(project: Project, text: String) extends {
  private var vFile = new LightVirtualFile("Dummy.scala",
    ScalaFileType.INSTANCE, text)
  private var provider = new SingleRootFileViewProvider(
    PsiManager.getInstance(project), vFile, true)
} with ScalaFileImpl(provider) with JavaCodeFragment with ScDeclarationSequenceHolder {
  getViewProvider.forceCachedPsi(this)

  override def getViewProvider: SingleRootFileViewProvider = provider

  private var thisType: PsiType = null
  private var superType: PsiType = null
  private var exceptionHandler: ExceptionHandler = null
  private var resolveScope: GlobalSearchScope = null
  private var filter: IntentionActionsFilter = null
  private var imports: mutable.HashSet[String] = new mutable.HashSet

  def getThisType: PsiType = thisType

  def setThisType(psiType: PsiType) {
    thisType = psiType
  }

  def getSuperType: PsiType = superType

  def setSuperType(superType: PsiType) {
    this.superType = superType
  }

  def importsToString(): String = {
    imports.mkString(",")
  }

  def addImportsFromString(imports: String) {
    this.imports ++= imports.split(',').filter(_.nonEmpty)
  }

  def setVisibilityChecker(checker: VisibilityChecker) {}

  def getVisibilityChecker: VisibilityChecker = VisibilityChecker.EVERYTHING_VISIBLE

  def setExceptionHandler(checker: ExceptionHandler) {
    exceptionHandler = checker
  }

  def getExceptionHandler: ExceptionHandler = exceptionHandler

  def forceResolveScope(scope: GlobalSearchScope) {
    resolveScope = scope
  }

  def getForcedResolveScope: GlobalSearchScope = resolveScope

  def setIntentionActionsFilter(filter: IntentionActionsFilter) {
    this.filter = filter
  }

  def getIntentionActionsFilter: IntentionActionsFilter = filter

  override def isScriptFile: Boolean = false

  override def isScriptFile(withCashing: Boolean): Boolean = false

  override def addImportForPath(path: String, ref: PsiElement) {
    imports += path
    myManager.beforeChange(false)
    val project: Project = myManager.getProject
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val document: Document = psiDocumentManager.getDocument(this)
    UndoManager.getInstance(project).undoableActionPerformed(
      new ScalaCodeFragment.ImportClassUndoableAction(path, document, imports)
    )
    val newRef = ref match {
      case st: ScStableCodeReferenceElement if st.resolve() == null =>
        Some(ScalaPsiElementFactory.createReferenceFromText(st.getText, st.getParent, st))
      case expr: ScReferenceExpression if expr.resolve() == null =>
        Some(ScalaPsiElementFactory.createExpressionFromText(expr.getText, expr).asInstanceOf[ScReferenceExpression])
      case _ => None
    }
    newRef match {
      case Some(r) if r.resolve() != null => ref.replace(r)
      case _ =>
    }
  }

  override def addImportsForPaths(paths: Seq[String], refsContainer: PsiElement): Unit = {
    paths.foreach(addImportForPath(_, refsContainer))
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    for (qName <- imports) {
      val imp = ScalaPsiElementFactory.createImportFromTextWithContext("import _root_." + qName, this, this)
      if (!imp.processDeclarations(processor, state, lastParent, place)) return false
    }
    if (!super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)) return false
    true
  }

  override def clone(): PsiFileImpl = {
    val clone = cloneImpl(calcTreeElement.clone.asInstanceOf[FileElement]).asInstanceOf[ScalaCodeFragment]
    clone.imports = this.imports
    clone.vFile = new LightVirtualFile("Dummy.scala",
      ScalaFileType.INSTANCE, getText)
    clone.provider = provider.clone().asInstanceOf[SingleRootFileViewProvider]
    clone.provider.forceCachedPsi(clone)
    clone
  }
}

object ScalaCodeFragment {

  private class ImportClassUndoableAction(path: String, document: Document,
                                          imports: mutable.HashSet[String]) extends BasicUndoableAction {
    def undo() {
      imports -= path
    }

    def redo() {
      imports += path
    }
  }

}