package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.util.concurrency.Semaphore
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import com.intellij.xdebugger.{XDebugSession, XDebuggerManager}
import com.sun.jdi.{ObjectReference, Value}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class ScalaCodeFragmentFactory extends CodeFragmentFactory {
  override def createCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    implicit val p: Project = project
    val fragment = createCodeFragmentInner(item, wrapContext(context))

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
              case tp: PsiClassType =>
                //noinspection ScalaRedundantCast
                tp.resolve().asInstanceOf[PsiClass]
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

    fragment.putCopyableUserData(ScalaRuntimeTypeEvaluator.KEY, evaluateType: ScExpression => ScType)
    fragment
  }

  private def createCodeFragmentInner(item: TextWithImports, context: PsiElement)
                                     (implicit project: Project): ScalaCodeFragment = {
    val fragment = ScalaCodeFragment(item.getText, context)
    fragment.addImportsFromString(item.getImports)
    fragment.putUserData(DefaultCodeFragmentFactory.KEY, "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR")
    fragment
  }

  override def createPresentationCodeFragment(item: TextWithImports, context: PsiElement, project: Project): JavaCodeFragment = {
    createCodeFragment(item, context, project)
  }

  override def isContextAccepted(contextElement: PsiElement): Boolean = {
    if (contextElement.isInstanceOf[PsiCodeBlock]) {
      return contextElement.getContext != null && contextElement.getContext.getContext != null &&
        contextElement.getContext.getContext.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
    }
    if (contextElement == null) return false
    contextElement.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
  }

  override def getFileType: LanguageFileType = ScalaFileType.INSTANCE

  override def getEvaluatorBuilder: EvaluatorBuilder = ScalaEvaluatorBuilder

  private def wrapContext(originalContext: PsiElement)
                         (implicit project: Project): PsiElement = {
    if (project.isDefault) return originalContext
    var context: PsiElement = originalContext
    val session: XDebugSession = XDebuggerManager.getInstance(project).getCurrentSession
    if (session != null) {
      val markers = session.asInstanceOf[XDebugSessionImpl].getValueMarkers
      val markupMap = if (markers != null) markers.getAllMarkers.asScala.toMap else null
      if (markupMap != null && markupMap.nonEmpty) {
        val (variablesText, reverseMap): (String, Map[String, Value]) = markupVariablesText(markupMap)
        val offset: Int = variablesText.length - 1
        val textWithImports: TextWithImportsImpl = new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, variablesText, "", getFileType)
        val codeFragment: JavaCodeFragment = createCodeFragmentInner(textWithImports, context)
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

  private def markupVariablesText[T](markupMap: Map[T, ValueMarkup]): (String, Map[String, Value]) = {
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