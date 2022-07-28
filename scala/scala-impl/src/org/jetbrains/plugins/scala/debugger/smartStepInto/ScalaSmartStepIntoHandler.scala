package org.jetbrains.plugins.scala.debugger.smartStepInto

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.actions.{JvmSmartStepIntoHandler, MethodSmartStepTarget, SmartStepTarget}
import com.intellij.debugger.engine.MethodFilter
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Range
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.concurrency.{Promise, Promises}
import org.jetbrains.plugins.scala.codeInspection.collections.{MethodRepr, stripped}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.debugger.filters.ScalaDebuggerSettings
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import java.util.{Collections, List => JList}
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

class ScalaSmartStepIntoHandler extends JvmSmartStepIntoHandler {

  override def findStepIntoTargets(position: SourcePosition, session: DebuggerSession): Promise[JList[SmartStepTarget]] = {
    if (ScalaDebuggerSettings.getInstance().ALWAYS_SMART_STEP_INTO) {
      findSmartStepTargetsAsync(position, session)
    }
    else {
      Promises.rejectedPromise[JList[SmartStepTarget]]
    }
  }

  override def findSmartStepTargets(position: SourcePosition): JList[SmartStepTarget] = {
    val line: Int = position.getLine
    if (line < 0) {
      return Collections.emptyList[SmartStepTarget]
    }

    Stats.trigger(FeatureKey.debuggerSmartStepInto)

    val (element, doc) =
      (for {
        sf @ (_sf: ScalaFile) <- position.getFile.toOption
        if !sf.isCompiled
        vFile <- sf.getVirtualFile.toOption
        doc <- FileDocumentManager.getInstance().getDocument(vFile).toOption
        if doc.getLineCount > line
      } yield {
        val startOffset: Int = doc.getLineStartOffset(line)
        val offset: Int = CharArrayUtil.shiftForward(doc.getCharsSequence, startOffset, " \t{")
        val element: PsiElement = sf.findElementAt(offset)
        (element, doc)
      }) match {
        case Some((null, _)) => return Collections.emptyList[SmartStepTarget]
        case Some((e, d)) => (e, d)
        case _ => return Collections.emptyList[SmartStepTarget]
      }

    val lineStart = doc.getLineStartOffset(line)
    val lineRange = new TextRange(lineStart, doc.getLineEndOffset(line))
    val maxElement = maxElementOnLine(element, lineStart)

    val lineToSkip = new Range[Integer](line, line)
    def intersectsWithLineRange(elem: PsiElement) = {
      lineRange.intersects(elem.getTextRange)
    }

    val collector = new TargetCollector(lineToSkip, intersectsWithLineRange)
    maxElement.accept(collector)
    maxElement.nextSiblings
            .takeWhile(intersectsWithLineRange)
            .foreach(_.accept(collector))
    collector.result.sortBy(_.getHighlightElement.getTextOffset).asJava
  }
  override def isAvailable(position: SourcePosition): Boolean = {
    val file: PsiFile = position.getFile
    file.isInstanceOf[ScalaFile]
  }

  override def createMethodFilter(stepTarget: SmartStepTarget): MethodFilter = {
    stepTarget match {
      case methodTarget: MethodSmartStepTarget =>
        val scalaFilter = methodTarget.getMethod match {
          case f @ (_: ScMethodLike | _: FakeAnonymousClassConstructor) if stepTarget.needsBreakpointRequest() =>
            ScalaBreakpointMethodFilter.from(f, stepTarget.getCallingExpressionLines)
          case fun: ScMethodLike =>
            Some(new ScalaMethodFilter(fun, stepTarget.getCallingExpressionLines))
          case _ => None
        }
        scalaFilter.getOrElse(super.createMethodFilter(stepTarget))
      case ScalaFunExprSmartStepTarget(_, stmts) =>
        ScalaBreakpointMethodFilter.from(None, stmts, stepTarget.getCallingExpressionLines)
                .getOrElse(super.createMethodFilter(stepTarget))
      case _ => super.createMethodFilter(stepTarget)
    }
  }

  @tailrec
  private def maxElementOnLine(startElem: PsiElement, lineStart: Int): PsiElement = {
    val parent = startElem.getParent
    parent match {
      case _: ScBlock | null => startElem
      case p if p.getTextRange.getStartOffset >= lineStart => maxElementOnLine(parent, lineStart)
      case _ => startElem
    }
  }

  private class TargetCollector(noStopAtLines: Range[Integer], elementFilter: PsiElement => Boolean) extends ScalaRecursiveElementVisitor {
    val result: ArrayBuffer[SmartStepTarget] = ArrayBuffer[SmartStepTarget]()

    override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition): Unit = {
      if (!elementFilter(templ)) return

      val extBl = templ.extendsBlock
      var label = ""

      def findConstructorInvocation(typeElem: ScTypeElement): Option[ScConstructorInvocation] = typeElem match {
        case p: ScParameterizedTypeElement => p.findConstructorInvocation
        case s: ScSimpleTypeElement => s.findConstructorInvocation
        case _ => None
      }

      def addConstructor(): Unit = {
        for {
          tp <- extBl.templateParents
          typeElem <- tp.typeElements.headOption
          constrInvocation <- findConstructorInvocation(typeElem)
          ref <- constrInvocation.reference
        } {
          label = constrInvocation.simpleTypeElement.fold("")(ste => s"new ${ste.getText}.")

          val generateAnonClass = DebuggerUtil.generatesAnonClass(templ)
          val method = ref.resolve() match {
            case m: PsiMethod if !generateAnonClass => m
            case _ => new FakeAnonymousClassConstructor(templ, ref.refName)
          }
          result += new MethodSmartStepTarget(method, "new ", constrInvocation, /*needBreakpointRequest = */ generateAnonClass, noStopAtLines)
        }
      }

      def addMethodsIfInArgument(): Unit = {
        PsiTreeUtil.getParentOfType(templ, classOf[MethodInvocation]) match {
          case MethodRepr(_, _, _, args) if args.map(stripped).contains(templ) =>
            extBl.templateBody match {
              case Some(tb) =>
                for {
                  fun @ (_f: ScFunctionDefinition) <- tb.functions
                  body <- fun.body
                } {
                  result += new MethodSmartStepTarget(fun, label, body, true, noStopAtLines)
                }
              case _ =>
            }
          case _ =>
        }
      }

      addConstructor()
      addMethodsIfInArgument()
    }

    override def visitExpression(expr: ScExpression): Unit = {
      if (!elementFilter(expr)) return

      expr.implicitElement().collect {
        case method: PsiMethod => method
      }.filter(_.isPhysical) // synthetic conversions are created for implicit classes
        .map(new MethodSmartStepTarget(_, "implicit ", expr, false, noStopAtLines))
        .foreach(result.+=)

      expr match {
        case ScalaPsiUtil.MethodValue(m) =>
          result += new MethodSmartStepTarget(m, null, expr, true, noStopAtLines)
          return
        case FunExpressionTarget(stmts, presentation) =>
          result += new ScalaFunExprSmartStepTarget(expr, stmts, presentation, noStopAtLines)
          return //stop at function expression
        case ref: ScReferenceExpression =>
          ref.resolve() match {
            case fun: ScFunctionDefinition if fun.name == "apply" && ref.refName != "apply" =>
              val prefix = s"${ref.refName}."
              result += new MethodSmartStepTarget(fun, prefix, ref.nameId, false, noStopAtLines)
            case (f: ScFunctionDefinition) && ContainingClass(cl: ScClass) if cl.getModifierList.hasModifierProperty("implicit") =>
              val isActuallyImplicit = ref.qualifier.flatMap(_.implicitElement()).isDefined
              val prefix = if (isActuallyImplicit) "implicit " else null
              result += new MethodSmartStepTarget(f, prefix, ref.nameId, false, noStopAtLines)
            case fun: PsiMethod =>
              result += new MethodSmartStepTarget(fun, null, ref.nameId, false, noStopAtLines)
            case _ =>
          }
        case _ =>
      }
      super.visitExpression(expr)
    }

    override def visitPattern(pat: ScPattern): Unit = {
      if (!elementFilter(pat)) return

      val ref = pat match {
        case cp: ScConstructorPattern =>  Some(cp.ref)
        case ip: ScInfixPattern => Some(ip.operation)
        case _ => None
      }
      ref match {
        case Some(r @ ResolvesTo(f: ScFunctionDefinition)) =>
          val prefix = s"${r.refName}."
          result += new MethodSmartStepTarget(f, prefix, r.nameId, false, noStopAtLines)
        case _ =>
      }
      super.visitPattern(pat)
    }
  }

}
