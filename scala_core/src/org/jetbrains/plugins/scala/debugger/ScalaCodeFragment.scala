package org.jetbrains.plugins.scala.debugger

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.psi.search.GlobalSearchScope

import java.lang.String
import lang.psi.api.ScalaFile
import lang.psi.impl.ScalaFileImpl
import com.intellij.psi._

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.02.2009
 */

class ScalaCodeFragment(project: Project, text: CharSequence) extends ScalaFileImpl(
  new SingleRootFileViewProvider(PsiManager.getInstance(project),
        new LightVirtualFile(
            "Dummy.scala",
            ScalaFileType.SCALA_FILE_TYPE,
            text), true)
  ) with JavaCodeFragment {
  import JavaCodeFragment._
  import IntentionFilterOwner._

  private var thisType: PsiType = null
  private var superType: PsiType = null
  private var exceptionHandler: ExceptionHandler = null
  private var filter: IntentionActionsFilter = null
  private var resolveScope: GlobalSearchScope = null

  def getForcedResolveScope: GlobalSearchScope = resolveScope

  def forceResolveScope(scope: GlobalSearchScope): Unit = resolveScope = scope

  def getIntentionActionsFilter: IntentionActionsFilter = filter

  def setIntentionActionsFilter(filter: IntentionActionsFilter): Unit = this.filter = filter

  def addImportsFromString(imports: String): Unit = {}

  def setSuperType(superType: PsiType): Unit = this.superType = superType

  def getVisibilityChecker: VisibilityChecker = VisibilityChecker.EVERYTHING_VISIBLE

  def getThisType: PsiType = thisType

  def setVisibilityChecker(checker: VisibilityChecker): Unit = {}

  def importsToString: String = ""

  def getExceptionHandler: ExceptionHandler = exceptionHandler

  def setExceptionHandler(checker: ExceptionHandler): Unit = exceptionHandler = checker

  def getSuperType: PsiType = superType

  def setThisType(psiType: PsiType): Unit = thisType = psiType

  def importClass(aClass: PsiClass): Boolean = false
}