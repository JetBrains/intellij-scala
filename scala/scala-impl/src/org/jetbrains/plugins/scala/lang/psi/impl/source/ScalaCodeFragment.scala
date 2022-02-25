package org.jetbrains.plugins.scala.lang.psi.impl.source

import com.intellij.openapi.command.undo.{BasicUndoableAction, UndoManager}
import com.intellij.openapi.project.Project
import com.intellij.psi.IntentionFilterOwner.IntentionActionsFilter
import com.intellij.psi.JavaCodeFragment.{ExceptionHandler, VisibilityChecker}
import com.intellij.psi._
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.{DelegatingGlobalSearchScope, GlobalSearchScope}
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder.ImportPath
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaFileImpl, ScalaPsiElementFactory}

import scala.collection.mutable

final class ScalaCodeFragment private(private var viewProvider: SingleRootFileViewProvider)
  extends ScalaFileImpl(viewProvider)
    with JavaCodeFragment
    with IntentionFilterOwner
    with ScDeclarationSequenceHolder {

  import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

  getViewProvider.forceCachedPsi(this)

  private var thisType: PsiType = _
  private var superType: PsiType = _
  private var exceptionHandler: ExceptionHandler = _
  private var resolveScope: GlobalSearchScope = _
  private var filter: IntentionActionsFilter = _
  private var imports = mutable.HashSet.empty[String]

  override def getThisType: PsiType = thisType

  override def setThisType(thisType: PsiType): Unit = {
    this.thisType = thisType
  }

  override def getSuperType: PsiType = superType

  override def setSuperType(superType: PsiType): Unit = {
    this.superType = superType
  }

  override def importsToString(): String = {
    imports.mkString(",")
  }

  override def addImportsFromString(imports: String): Unit = {
    this.imports ++= imports.split(',').filter(_.nonEmpty)
  }

  override def setVisibilityChecker(checker: VisibilityChecker): Unit = {}

  override def getVisibilityChecker: VisibilityChecker = VisibilityChecker.EVERYTHING_VISIBLE

  override def setExceptionHandler(exceptionHandler: ExceptionHandler): Unit = {
    this.exceptionHandler = exceptionHandler
  }

  override def getExceptionHandler: ExceptionHandler = exceptionHandler

  override def forceResolveScope(resolveScope: GlobalSearchScope): Unit = {
    val wrapperScope = new DelegatingGlobalSearchScope(resolveScope) with GlobalSearchScopeWithRecommendedResultsSorting
    this.resolveScope = wrapperScope
  }

  override def getForcedResolveScope: GlobalSearchScope = resolveScope

  override def getResolveScope: GlobalSearchScope =
    super.getResolveScope

  override def setIntentionActionsFilter(filter: IntentionActionsFilter): Unit = {
    this.filter = filter
  }

  override def getIntentionActionsFilter: IntentionActionsFilter = filter

  override def getViewProvider: SingleRootFileViewProvider = viewProvider

  private def myAddImportForPath(path: ImportPath, ref: ScReference): Unit = {
    //NOTE path.aliasName is not handled, I don't know at what conditions it might be actual here
    val pathFqn = path.qualifiedName

    imports += pathFqn
    myManager.beforeChange(false)

    UndoManager.getInstance(myManager.getProject)
      .undoableActionPerformed(new ImportClassUndoableAction(pathFqn))

    val newRef = ref match {
      case st: ScStableCodeReference if st.resolve() == null =>
        Some(createReferenceFromText(st.getText, st.getParent, st))
      case expr: ScReferenceExpression if expr.resolve() == null =>
        Some(createExpressionFromText(expr.getText, expr).asInstanceOf[ScReferenceExpression])
      case _ => None
    }

    newRef match {
      case Some(r) if r.resolve() != null =>
        ref.replace(r)
      case _ =>
    }
  }

  override protected[psi] def addImportsForPathsImpl(paths: Seq[ImportPath], refsContainer: PsiElement): Unit = {
    refsContainer match {
      case ref: ScReference =>
        paths.foreach(myAddImportForPath(_, ref))
      case _ =>
    }
  }

  @Deprecated
  override def importClass(aClass: PsiClass): Boolean = {
    addImportForClass(aClass, ref = null)
    true
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    for (qName <- imports) {
      val imp = ScalaPsiElementFactory.createImportFromText(s"import _root_.$qName", this, this)
      if (!imp.processDeclarations(processor, state, lastParent, place))
        return false
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

    override def undo(): Unit = {
      imports -= path
    }

    override def redo(): Unit = {
      imports += path
    }
  }

}

object ScalaCodeFragment {

  def apply(
    text: String,
    context: PsiElement = null,
    child: PsiElement = null
  )(implicit project: Project): ScalaCodeFragment = {
    val language = Option(context)
      .map(_.getContainingFile.getLanguage)
      .filter(_.isKindOf(ScalaLanguage.INSTANCE))
      .getOrElse(ScalaLanguage.INSTANCE)

    val viewProvider = new SingleRootFileViewProvider(
      PsiManager.getInstance(project),
      new LightVirtualFile("Dummy.scala", language, text),
      true
    )
    val fragment = new ScalaCodeFragment(viewProvider)
    fragment.context = context
    fragment.child = child
    fragment
  }
}

/**
 * Marker trait which can be mixed into some scope (e.g. using wrapper [[DelegatingGlobalSearchScope]])
 * This marker indicates that resolve results, obtained from this scope, should be sorted using [[GlobalSearchScope#compare]]
 *
 * Sorting might be required when some scope contains collisions for the same fully-qualified-name
 * (e.g. if the scope contains multiple jar files for the same library and some class is resolved into all of them)
 *
 * This can happen e.g. when global project scope is used ([[GlobalSearchScope.allScope]],
 * which includes all libraries jars (potentially with different library versions)
 * Even if it's a simple sbt project with one module, the issue is actual: we have a special `-build` module which
 * contains jars for sbt itself and which uses it's own scala-library.jar. This module is also included into the global
 * scope resulting into conflicting scala-library jars
 *
 * Example of global project scope can be found in debugger evaluator subsystem
 * (this was the primary reason for this marker trait).
 * In this method
 * [[com.intellij.debugger.engine.evaluation.CodeFragmentFactoryContextWrapper.prepareResolveScope]]
 * our resolve scope [[ScalaCodeFragment.getResolveScope]] is wrapped with a another scope which includes global scope.
 * Notice that [[GlobalSearchScope#compare]] method is being implemented implying that it will be used
 * (although there is no any contract which says that it should be used)
 *
 * @note we can't force all users of this scope to sort the results, because there is no any documented contract for
 *       [[GlobalSearchScope#compare]] usage. Even in Java plugin it's used not everywhere
 * @note it's also possible that somewhere under the hood during resolve process this scope will be wrapped with another
 *       wrapper scope. In this case this marker will be "lost". In theory there might be some issues related to that.
 *       Ideally this marker trait should be integrated into the IntelliJ Platform and it probably should be
 *       preserved when doing operations on scope: wrap/union/intersect, etc...
 *       Also, idially, [[GlobalSearchScope#compare]] contract should be provided
 * @note Why do we even need wrapping [[ScalaCodeFragment.getResolveScope]] with [[GlobalSearchScope.allScope]]?<br>
 *       According to Egor Ushakov: "as far as I remember it's in order to be able to call any methods"<br>
 *       For example, we might split our project into 3 modules A -> B -> C which are later packaged into a single jar (`->` means "depends on").
 *       If we debug some file in module "C" we might still want to evaluate something from module "A" without leaving current context
 *       (though the evaluated method is not accessible from module "C" resolve scope)
 */
trait GlobalSearchScopeWithRecommendedResultsSorting {
  self: GlobalSearchScope =>
}