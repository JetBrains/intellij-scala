package org.jetbrains.plugins.scala.debugger.evaluation

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.IntentionFilterOwner.IntentionActionsFilter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.JavaCodeFragment.{VisibilityChecker, ExceptionHandler}
import com.intellij.psi.{PsiType, PsiManager, SingleRootFileViewProvider, JavaCodeFragment}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCodeFragment(project: Project, text: String) extends ScalaFileImpl(new SingleRootFileViewProvider(
  PsiManager.getInstance(project), new LightVirtualFile("Dummy.scala",
    ScalaFileType.SCALA_FILE_TYPE, text), true)) with JavaCodeFragment {
  getViewProvider.asInstanceOf[SingleRootFileViewProvider].forceCachedPsi(this)

  private var thisType: PsiType = null
  private var superType: PsiType = null
  private var exceptionHandler: ExceptionHandler = null
  private var resolveScope: GlobalSearchScope = null
  private var filter: IntentionActionsFilter = null

  def getThisType: PsiType = thisType

  def setThisType(psiType: PsiType) {thisType = psiType}

  def getSuperType: PsiType = superType

  def setSuperType(superType: PsiType) {this.superType = superType}

  def importsToString(): String = ""

  def addImportsFromString(imports: String) {}

  def setVisibilityChecker(checker: VisibilityChecker) {}

  def getVisibilityChecker: VisibilityChecker = VisibilityChecker.EVERYTHING_VISIBLE

  def setExceptionHandler(checker: ExceptionHandler) {exceptionHandler = checker}

  def getExceptionHandler: ExceptionHandler = exceptionHandler

  def forceResolveScope(scope: GlobalSearchScope) {resolveScope = scope}

  def getForcedResolveScope: GlobalSearchScope = resolveScope

  def setIntentionActionsFilter(filter: IntentionActionsFilter) {this.filter = filter}

  def getIntentionActionsFilter: IntentionActionsFilter = filter

  override def isScriptFile: Boolean = false
}