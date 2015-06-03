package org.jetbrains.plugins.scala.debugger.smartStepInto

import java.util.{Collections, List => JList}
import javax.swing.Icon

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.actions.{JvmSmartStepIntoHandler, MethodSmartStepTarget, SmartStepTarget}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Range
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.codeInspection.collections.{MethodRepr, stripped}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiNamedElementExt, ResolvesTo, childOf}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types.StdType

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.12
 */

class ScalaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
  override def findSmartStepTargets(position: SourcePosition): JList[SmartStepTarget] = {
    val line: Int = position.getLine
    if (line < 0) {
      return Collections.emptyList[SmartStepTarget]
    }
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

    def linesToSkip(): Range[Integer] = {
      val element = maxElement match {
        case (_: ScCaseClause) childOf (_ childOf (mc: ScMatchStmt)) => mc
        case (_: ScCaseClauses) childOf (mc: ScMatchStmt) => mc
        case _ => maxElement
      }
      val range = element.getTextRange
      val startLine = doc.getLineNumber(range.getStartOffset)
      val endLine = doc.getLineNumber(range.getEndOffset)
      new Range[Integer](startLine, endLine)
    }

    val collector = new TargetCollector(linesToSkip())
    maxElement.accept(collector)
    maxElement.nextSiblings
            .takeWhile(s => lineRange.intersects(s.getTextRange))
            .foreach(_.accept(collector))
    collector.result.asJava
  }
  def isAvailable(position: SourcePosition): Boolean = {
    val file: PsiFile = position.getFile
    file.isInstanceOf[ScalaFile]
  }

  override def createMethodFilter(stepTarget: SmartStepTarget) = {
    stepTarget match {
      case methodTarget: MethodSmartStepTarget =>
        val scalaFilter = methodTarget.getMethod match {
          case fun: ScFunction if fun.isLocal =>
            Some(new LocalFunctionMethodFilter(fun, stepTarget.getCallingExpressionLines))
          case psiMethod if stepTarget.needsBreakpointRequest() =>
            ScalaBreakpointMethodFilter.from(psiMethod, stepTarget.getCallingExpressionLines)
          case _ => None
        }
        scalaFilter.getOrElse(super.createMethodFilter(stepTarget))
      case ScalaFunExprSmartStepTarget(fExpr, stmts) =>
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

  private class TargetCollector(noStopAtLines: Range[Integer]) extends ScalaRecursiveElementVisitor {
    val result = ArrayBuffer[SmartStepTarget]()

    override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition): Unit = {
      val extBl = templ.extendsBlock
      var label = ""

      def findConstructorAndMethod: Option[(ScConstructor, PsiMethod)] = {
        for {
          tp <- extBl.templateParents
          typeElem <- tp.typeElements.headOption
          constr <- findConstructor(typeElem)
          ref <- constr.reference
          resolve @ (_: PsiMethod | _: PsiClass) <- ref.resolve().toOption
        } yield {
          resolve match {
            case m: PsiMethod => (constr, m)
            case i: PsiClass if i.isInterface => (constr, fakeConstructor(i.name))
          }
        }
      }

      def findConstructor(typeElem: ScTypeElement): Option[ScConstructor] = typeElem match {
        case p: ScParameterizedTypeElement => p.findConstructor
        case s: ScSimpleTypeElement => s.findConstructor
        case _ => None
      }

      def fakeConstructor(interfaceName: String): PsiMethod = {
        new FakePsiMethod(templ, interfaceName, Array.empty, StdType.UNIT, _ => false) {
          override def isConstructor: Boolean = true

          override def getIcon(flags: Int): Icon = Icons.CLASS

          override def equals(obj: scala.Any): Boolean = obj match {
            case fake: FakePsiMethod =>
              fake.navElement == this.navElement && fake.getName == this.getName
            case _ => false
          }

          override def hashCode() = navElement.hashCode() + 31 * getName.hashCode
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

      for ((constr, method) <- findConstructorAndMethod) {
        label = constr.simpleTypeElement.fold("")(ste => ste.getText + ".")
        result += new MethodSmartStepTarget(method, "new ", constr, true, noStopAtLines)
      }

      addMethodsIfInArgument()
    }

    override def visitExpression(expr: ScExpression) {
      val implicits = expr.getImplicitConversions()._2
      implicits match {
        case Some(f: PsiMethod) => result += new MethodSmartStepTarget(f, "implicit ", null, false, noStopAtLines)
        case _ =>
      }

      expr match {
        case FunExpressionTarget(stmts, presentation) =>
          result += new ScalaFunExprSmartStepTarget(expr, stmts, presentation, noStopAtLines)
          return //stop at function expression
        case ref: ScReferenceExpression =>
          ref.resolve() match {
            case fun: ScFunctionDefinition if fun.name == "apply" && ref.refName != "apply" =>
              val prefix = s"${ref.refName}."
              result += new MethodSmartStepTarget(fun, prefix, ref.nameId, false, noStopAtLines)
            case fun: PsiMethod =>
              result += new MethodSmartStepTarget(fun, null, ref.nameId, false, noStopAtLines)
            case _ =>
          }
        case f: ScForStatement =>
          f.getDesugarizedExpr match {
            case Some(e) =>
              e.accept(this)
              return
            case _ =>
          }
        case _ =>
      }
      super.visitExpression(expr)
    }

    override def visitPattern(pat: ScPattern): Unit = {
      val ref = pat match {
        case cp: ScConstructorPattern =>  Some(cp.ref)
        case ip: ScInfixPattern => Some(ip.refernece)
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
