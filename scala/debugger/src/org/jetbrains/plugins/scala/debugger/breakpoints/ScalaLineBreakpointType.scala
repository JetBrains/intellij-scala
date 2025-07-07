package org.jetbrains.plugins.scala.debugger.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.ui.breakpoints._
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.breakpoints.{XLineBreakpoint, XLineBreakpointType}
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.{XDebuggerUtil, XSourcePosition}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.debugger.{DebuggerBundle, ScalaLambdaSourcePosition, ScalaPositionManager, ScalaSourcePositionWithWholeLineHighlighted}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.statistics.ScalaDebuggerUsagesCollector

import java.util.Collections
import javax.swing.Icon

class ScalaLineBreakpointType extends JavaLineBreakpointType("scala-line", DebuggerBundle.message("line.breakpoints.tab.title")) {

  override def getDisplayName: String = DebuggerBundle.message("line.breakpoints.tab.title")

  override def canPutAt(@NotNull file: VirtualFile, line: Int, @NotNull project: Project): Boolean = {
    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null) return false
    if (!psiFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) return false
    val document = FileDocumentManager.getInstance.getDocument(file)
    if (document == null) return false
    psiFile match {
      case sf: ScalaFile if sf.isWorksheetFile => return false // we do not support debugging in worksheets yet
      case _ =>
    }

    var result: Boolean = false
    val processor: Processor[PsiElement] = {
      case ElementType(ScalaTokenTypes.kPACKAGE | ScalaTokenTypes.kIMPORT)  => false
      case _: PsiWhiteSpace                                                 => true
      case e if PsiTreeUtil.getParentOfType(e, classOf[PsiComment]) != null => true
      case e if PsiTreeUtil.getParentOfType(e, classOf[ScExpression], classOf[ScExtractorPattern], classOf[ScClass]) != null =>
        result = true
        false
      case _ => true
    }
    XDebuggerUtil.getInstance.iterateLine(project, document, line, processor)
    result
  }

  private type BreakpointVariant = XLineBreakpointType[JavaLineBreakpointProperties]#XLineBreakpointVariant

  private type JavaBPVariant = JavaLineBreakpointType#JavaBreakpointVariant

  @RequiresReadLock
  @NotNull
  override def computeVariants(@NotNull project: Project, @NotNull position: XSourcePosition): java.util.List[JavaBPVariant] = {
    val dumbService = DumbService.getInstance(project)
    if (dumbService.isDumb) return Collections.emptyList()

    val file = PsiManager.getInstance(project).findFile(position.getFile) match {
      case null => return Collections.emptyList()
      case sf: ScalaFile => sf
      case _ => return Collections.emptyList()
    }
    val line = position.getLine

    val positionsOnLine = ScalaPositionManager.positionsOnLine(file, line)
    val lambdas = ScalaPositionManager.filterLambdasOnLine(file, line, positionsOnLine)

    if (lambdas.isEmpty) return Collections.emptyList()

    val elementAtLine = SourcePosition.createFromLine(file, line).getElementAt

    val res = new java.util.LinkedList[JavaBPVariant]()

    val method = findContainingDefinition(elementAtLine, lambdas)

    val extraPriorityForLambdas = positionsOnLine.sizeCompare(lambdas) == 0
    for ((lambda, ordinal) <- lambdas.zipWithIndex) {
      val element = lambda match {
        case f: ScFunctionExpr => f.result.getOrElse(f)
        case e => e
      }
      res.addLast(new LambdaScalaBreakpointVariant(XSourcePositionImpl.createByElement(element), element, ordinal, extraPriorityForLambdas))
    }
    res.addFirst(new LineScalaBreakpointVariant(position, method.orNull))
    res.addFirst(new JavaBreakpointVariant(position, lambdas.size)) //adding all variants
    res
  }

  private def findContainingDefinition(elem: PsiElement, lambdas: Seq[PsiElement]): Option[PsiElement] =
    elem.withParentsInFile.collect {
      case c if ScalaPositionManager.isLambda(c) => c
      case m: PsiMethod => m
      case tb: ScTemplateBody => tb
      case ed: ScEarlyDefinitions => ed
      case c: ScClass => c
    }.find(!lambdas.contains(_))

  //noinspection InstanceOf
  override def matchesPosition(@NotNull breakpoint: LineBreakpoint[_], @NotNull position: SourcePosition): Boolean = {
    val method = getContainingMethod(breakpoint)
    if (method == null) return false

    if (!breakpoint.isInstanceOf[RunToCursorBreakpoint] && isMatchAll(breakpoint)) return true

    if (isLambda(breakpoint)) {
      ScalaDebuggerUsagesCollector.logLambdaBreakpoint(breakpoint.getProject)
      if (!position.isInstanceOf[ScalaLambdaSourcePosition]) return false
      val element = position.asInstanceOf[ScalaLambdaSourcePosition].lambda
      position.isInstanceOf[ScalaLambdaSourcePosition] &&
        ScalaPositionManager.isLambda(element) && element.getTextRange == method.getTextRange
    } else {
      position.isInstanceOf[ScalaSourcePositionWithWholeLineHighlighted] &&
        position.getLine == position.getElementAt.getLineNumber
    }
  }

  @Nullable
  override def getContainingMethod(@NotNull breakpoint: LineBreakpoint[_]): PsiElement = {
    val position: SourcePosition = breakpoint.getSourcePosition
    if (position == null || position.getElementAt == null) return null

    val ordinal = lambdaOrdinal(breakpoint)
    val lambdas = ScalaPositionManager.lambdasOnLine(position.getFile, position.getLine)
    if (!isLambda(breakpoint) || ordinal > lambdas.size - 1) {
      val element = position.getElementAt
      findContainingDefinition(element, lambdas).orNull
    } else lambdas(ordinal)
  }

  override def getHighlightRange(breakpoint: XLineBreakpoint[JavaLineBreakpointProperties]): TextRange = {
    BreakpointManager.getJavaBreakpoint(breakpoint) match {
      case lineBp: LineBreakpoint[_] if isLambda(lineBp) =>
        val dumbService = DumbService.getInstance(lineBp.getProject)
        if (dumbService.isDumb) {
          breakpoint match {
            case breakpointImpl: XLineBreakpointImpl[_] =>
              val project = lineBp.getProject
              ReadAction
                .nonBlocking[Unit](() => {
                  if (lineBp.isValid) {
                    getContainingMethod(lineBp) //populating caches outside edt
                  }
                })
                .finishOnUiThread(ModalityState.nonModal(), _ => {
                  val highlighter = breakpointImpl.getHighlighter
                  if (highlighter != null)
                    highlighter.dispose()
                  breakpointImpl.updateUI()
                })
                .coalesceBy(lineBp)
                .inSmartMode(project)
                .submit(AppExecutorUtil.getAppExecutorService)
            case _ =>
          }
          null
        }
        else Option(getContainingMethod(lineBp)).map {
          case f: ScFunctionExpr => f.result.getOrElse(f).getTextRange
          case e => e.getTextRange
        }.orNull
      case _ => null
    }

  }

  private def lambdaOrdinal(breakpoint: LineBreakpoint[_]): Integer = {
    val xBreakpoint = breakpoint.getXBreakpoint
    if (xBreakpoint != null) {
      xBreakpoint.getProperties match {
        case jp: JavaLineBreakpointProperties => jp.getLambdaOrdinal
        case _ => null
      }
    }
    else null
  }

  private def isLambda(breakpoint: LineBreakpoint[_]): Boolean = {
    val ordinal = lambdaOrdinal(breakpoint)
    ordinal != null && ordinal >= 0
  }

  private def isMatchAll(breakpoint: LineBreakpoint[_]): Boolean = lambdaOrdinal(breakpoint) == null

  override def getPriority: Int = super.getPriority + 1

  private final class LineScalaBreakpointVariant(position: XSourcePosition, @Nullable method: PsiElement)
    extends LineJavaBreakpointVariant(position, method, -1) {

    override def getIcon: Icon = method match {
      case e @ (_: PsiMethod | _: PsiClass | _: PsiFile) => e.getIcon(0)
      case _ => AllIcons.Debugger.Db_set_breakpoint
    }

    override def getText: String = method match {
      case c: ScClass => DebuggerBundle.message("breakpoint.location.constructor.of", c.name)
      case ed: ScEarlyDefinitions =>
        val clazz = PsiTreeUtil.getParentOfType(ed, classOf[ScTypeDefinition])
        if (clazz != null) DebuggerBundle.message("breakpoint.location.early.definitions.of", clazz.name)
        else DebuggerBundle.message("breakpoint.location.line.in.containing.block")
      case (_: ScFunction) & (named: ScNamedElement) => DebuggerBundle.message("breakpoint.location.line.in.function", named.name)
      case _: ScalaFile => DebuggerBundle.message("breakpoint.location.line.in.containing.file")
      case _ => DebuggerBundle.message("breakpoint.location.line.in.containing.block")
    }

    override def isLowPriority(firstLineElement: PsiElement): Boolean =
      firstLineElement.elementType == ScalaTokenTypes.tDOT
  }

  private final class LambdaScalaBreakpointVariant(position: XSourcePosition, @Nullable element: PsiElement, lambdaOrdinal: Int, hasExtraPriority: Boolean)
    extends LambdaJavaBreakpointVariant(position, element, lambdaOrdinal) {

    override def getPriority(project: Project): Int = {
      val priority = super.getPriority(project)
      val extraPriorty = if (hasExtraPriority) 50 else 0
      priority + extraPriorty
    }
  }
}
