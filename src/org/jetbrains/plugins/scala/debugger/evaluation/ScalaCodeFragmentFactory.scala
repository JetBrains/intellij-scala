package org.jetbrains.plugins.scala.debugger.evaluation

import java.util.concurrent.atomic.AtomicReference

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.DebuggerExpressionComboBox
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.util.concurrency.Semaphore
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import com.intellij.xdebugger.{XDebugSession, XDebuggerManager}
import com.sun.jdi.{ObjectReference, Value}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaCodeFragmentFactory extends CodeFragmentFactory {
  def createCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    val fragment = createCodeFragmentInner(item, context, project)
    fragment.setContext(wrapContext(project, context), null)

    def evaluateType(expr: ScExpression): ScType = {
      val debuggerContext: DebuggerContextImpl = DebuggerManagerEx.getInstanceEx(project).getContext
      val debuggerSession = debuggerContext.getDebuggerSession
      if (debuggerSession != null) {
        val semaphore: Semaphore = new Semaphore
        semaphore.down()
        val nameRef = new AtomicReference[PsiClass]
        val worker = new ScalaRuntimeTypeEvaluator(null, expr, debuggerContext, ProgressManager.getInstance.getProgressIndicator) {
          override def typeCalculationFinished(psiType: PsiType): Unit = {
            val psiClass = psiType match {
              case tp: PsiClassType => tp.resolve()
              case _ => null
            }
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
          return ScalaType.designator(psiClass)
        }
      }
      null
    }
    fragment.putCopyableUserData(ScalaRuntimeTypeEvaluator.KEY, evaluateType: (ScExpression) => ScType)
    fragment
  }

  private def createCodeFragmentInner(item: TextWithImports, context: PsiElement, project: Project): ScalaCodeFragment = {
    val fragment = new ScalaCodeFragment(project, item.getText)
    fragment.setContext(context, null)
    fragment.addImportsFromString(item.getImports)
    fragment.putUserData(DebuggerExpressionComboBox.KEY, "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR")
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

  private def wrapContext(project: Project, originalContext: PsiElement): PsiElement = {
    if (project.isDefault) return originalContext
    var context: PsiElement = originalContext
    val session: XDebugSession = XDebuggerManager.getInstance(project).getCurrentSession
    if (session != null) {
      val markers: XValueMarkers[_, _] = session.asInstanceOf[XDebugSessionImpl].getValueMarkers
      val markupMap = if (markers != null) markers.getAllMarkers.asScala.toMap else null
      if (markupMap != null && markupMap.nonEmpty) {
        val (variablesText, reverseMap): (String, Map[String, Value]) = markupVariablesText(markupMap)
        val offset: Int = variablesText.length - 1
        val textWithImports: TextWithImportsImpl = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, variablesText, "", getFileType)
        val codeFragment: JavaCodeFragment = createCodeFragmentInner(textWithImports, context, project)
        codeFragment.accept(new ScalaRecursiveElementVisitor() {
          override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
            val bindingPattern = pat.bindings.head
            val name: String = bindingPattern.name
            bindingPattern.putUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY, reverseMap.getOrElse(name, null))
          }
        })
        val newContext: PsiElement = codeFragment.findElementAt(offset)
        if (newContext != null) {
          context = newContext
        }
      }
    }
    context
  }

  private def markupVariablesText(markupMap: Map[_ <: Any, ValueMarkup]): (String, Map[String, Value]) = {
    val reverseMap = mutable.Map[String, Value]()
    val names = markupMap.collect {
      case (obj: ObjectReference, markup: ValueMarkup) if StringUtil.isJavaIdentifier(markup.getText) =>
        val labelName = markup.getText + CodeFragmentFactoryContextWrapper.DEBUG_LABEL_SUFFIX
        reverseMap.put(labelName, obj)
        labelName
    }
    val text = names.map(n => s"val $n: AnyRef = _").mkString("\n")
    (text, reverseMap.toMap)
  }
}