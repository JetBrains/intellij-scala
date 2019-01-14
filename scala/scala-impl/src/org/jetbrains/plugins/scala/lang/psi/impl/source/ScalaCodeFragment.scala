package org.jetbrains.plugins.scala
package lang
package psi
package impl
package source

import com.intellij.openapi.command.undo.{BasicUndoableAction, UndoManager}
import com.intellij.openapi.project.Project
import com.intellij.psi.IntentionFilterOwner.IntentionActionsFilter
import com.intellij.psi.JavaCodeFragment.{ExceptionHandler, VisibilityChecker}
import com.intellij.psi._
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  */
final class ScalaCodeFragment(private var viewProvider: SingleRootFileViewProvider)
  extends ScalaFileImpl(viewProvider)
    with JavaCodeFragment
    with IntentionFilterOwner
    with ScDeclarationSequenceHolder {

  import ScalaPsiElementFactory._

  getViewProvider.forceCachedPsi(this)

  def this(project: Project, text: String) = this(
    new SingleRootFileViewProvider(
      PsiManager.getInstance(project),
      new LightVirtualFile("Dummy.scala", ScalaFileType.INSTANCE, text),
      true)
  )

  private var thisType: PsiType = _
  private var superType: PsiType = _
  private var exceptionHandler: ExceptionHandler = _
  private var resolveScope: GlobalSearchScope = _
  private var filter: IntentionActionsFilter = _
  private var imports = mutable.HashSet.empty[String]

  def getThisType: PsiType = thisType

  def setThisType(thisType: PsiType): Unit = {
    this.thisType = thisType
  }

  def getSuperType: PsiType = superType

  def setSuperType(superType: PsiType): Unit = {
    this.superType = superType
  }

  def importsToString(): String = {
    imports.mkString(",")
  }

  def addImportsFromString(imports: String): Unit = {
    this.imports ++= imports.split(',').filter(_.nonEmpty)
  }

  def setVisibilityChecker(checker: VisibilityChecker): Unit = {}

  def getVisibilityChecker: VisibilityChecker = VisibilityChecker.EVERYTHING_VISIBLE

  def setExceptionHandler(exceptionHandler: ExceptionHandler): Unit = {
    this.exceptionHandler = exceptionHandler
  }

  def getExceptionHandler: ExceptionHandler = exceptionHandler

  def forceResolveScope(resolveScope: GlobalSearchScope): Unit = {
    this.resolveScope = resolveScope
  }

  def getForcedResolveScope: GlobalSearchScope = resolveScope

  def setIntentionActionsFilter(filter: IntentionActionsFilter): Unit = {
    this.filter = filter
  }

  def getIntentionActionsFilter: IntentionActionsFilter = filter

  override def getViewProvider: SingleRootFileViewProvider = viewProvider

  override def isScriptFileImpl: Boolean = false

  override def addImportForPath(path: String, refsContainer: PsiElement): Unit = {
    imports += path
    myManager.beforeChange(false)

    UndoManager.getInstance(myManager.getProject)
      .undoableActionPerformed(new ImportClassUndoableAction(path))

    val newRef = refsContainer match {
      case st: ScStableCodeReferenceElement if st.resolve() == null =>
        Some(createReferenceFromText(st.getText, st.getParent, st))
      case expr: ScReferenceExpression if expr.resolve() == null =>
        Some(createExpressionFromText(expr.getText, expr).asInstanceOf[ScReferenceExpression])
      case _ => None
    }

    newRef match {
      case Some(r) if r.resolve() != null => refsContainer.replace(r)
      case _ =>
    }
  }

  override def addImportsForPaths(paths: Seq[String], refsContainer: PsiElement): Unit = {
    paths.foreach(addImportForPath(_, refsContainer))
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    for (qName <- imports) {
      val imp = createImportFromTextWithContext(s"import _root_.$qName", this, this)
      if (!imp.processDeclarations(processor, state, lastParent, place)) return false
    }

    super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)
  }

  override def clone(): PsiFileImpl = {
    val fileElement = calcTreeElement.clone.asInstanceOf[FileElement]
    val clone = cloneImpl(fileElement).asInstanceOf[ScalaCodeFragment]
    clone.myOriginalFile = this
    clone.imports = imports

    val fileManager = getManager.asInstanceOf[PsiManagerEx].getFileManager
    val virtualFile = new LightVirtualFile(getName, getLanguage, getText)
    val cloneViewProvider = fileManager.createFileViewProvider(virtualFile, false).asInstanceOf[SingleRootFileViewProvider]

    cloneViewProvider.forceCachedPsi(clone)
    clone.viewProvider = cloneViewProvider

    clone
  }

  private class ImportClassUndoableAction(path: String) extends BasicUndoableAction {

    def undo(): Unit = {
      imports -= path
    }

    def redo(): Unit = {
      imports += path
    }
  }

}