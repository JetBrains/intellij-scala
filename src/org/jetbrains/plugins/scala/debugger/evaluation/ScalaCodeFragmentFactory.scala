package org.jetbrains.plugins.scala.debugger.evaluation

import java.util.concurrent.atomic.AtomicReference

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder
import com.intellij.debugger.engine.evaluation.{CodeFragmentFactory, TextWithImports}
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.DebuggerExpressionComboBox
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCodeFragmentFactory extends CodeFragmentFactory {
  def createCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val fragment = new ScalaCodeFragment(project, item.getText)
    fragment.setContext(context, null)
    fragment.addImportsFromString(item.getImports)
    fragment.putUserData(DebuggerExpressionComboBox.KEY, "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR")

    def evaluateType(expr: ScExpression): ScType = {
      val debuggerContext: DebuggerContextImpl = DebuggerManagerEx.getInstanceEx(project).getContext
      val debuggerSession = debuggerContext.getDebuggerSession
      if (debuggerSession != null) {
        val semaphore: Semaphore = new Semaphore
        semaphore.down()
        val nameRef = new AtomicReference[PsiClass]
        val worker = new ScalaRuntimeTypeEvaluator(null, expr, debuggerContext, ProgressManager.getInstance.getProgressIndicator) {
          protected def typeCalculationFinished(@Nullable psiClass: PsiClass) {
            nameRef.set(psiClass)
            semaphore.up()
          }
        }
        debuggerContext.getDebugProcess.getManagerThread.invoke(worker)
        var i: Int = 0
        while (i < 50 && !semaphore.waitFor(20)) {
          ProgressManager.checkCanceled()
          i += 1
        }
        val psiClass: PsiClass = nameRef.get
        if (psiClass != null) {
          return ScType.designator(psiClass)
        }
      }
      null
    }
    fragment.putCopyableUserData(ScalaRuntimeTypeEvaluator.KEY, evaluateType: (ScExpression) => ScType)
    fragment
  }

  def createPresentationCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    createCodeFragment(item, context, project)
  }

  def isContextAccepted(contextElement: PsiElement): Boolean = {
    if (contextElement.isInstanceOf[PsiCodeBlock]) {
      return contextElement.getContext != null && contextElement.getContext.getContext != null &&
        contextElement.getContext.getContext.getLanguage == ScalaFileType.SCALA_LANGUAGE
    }
    if (contextElement == null) return false
    contextElement.getLanguage == ScalaFileType.SCALA_LANGUAGE
  }

  def getFileType: LanguageFileType = ScalaFileType.SCALA_FILE_TYPE

  def getEvaluatorBuilder: EvaluatorBuilder = ScalaEvaluatorBuilder
}