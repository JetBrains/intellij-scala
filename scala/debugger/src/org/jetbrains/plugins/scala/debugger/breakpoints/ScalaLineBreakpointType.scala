package org.jetbrains.plugins.scala.debugger.breakpoints

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.ui.breakpoints._
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.xdebugger.breakpoints.{XLineBreakpoint, XLineBreakpointType}
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.{XDebuggerUtil, XSourcePosition}
import org.jetbrains.annotations.{Nls, NotNull, Nullable}
import org.jetbrains.concurrency.{AsyncPromise, Promise}
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.scala.debugger.{DebuggerBundle, ScalaPositionManager}
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.statistics.ScalaDebuggerUsagesCollector
import org.jetbrains.plugins.scala.ScalaLanguage

import java.util.{Collections, List => JList}
import javax.swing.Icon
import scala.jdk.CollectionConverters._

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
      case e if PsiTreeUtil.getParentOfType(e, classOf[ScExpression], classOf[ScConstructorPattern], classOf[ScInfixPattern], classOf[ScClass]) != null =>
        result = true
        false
      case _ => true
    }
    XDebuggerUtil.getInstance.iterateLine(project, document, line, processor)
    result
  }

  private type BreakpointVariant = XLineBreakpointType[JavaLineBreakpointProperties]#XLineBreakpointVariant

  private type JavaBPVariant = JavaLineBreakpointType#JavaBreakpointVariant

  override def computeVariantsAsync(project: Project, position: XSourcePosition): Promise[JList[_ <: BreakpointVariant]] = {
    val promise = new AsyncPromise[JList[_ <: BreakpointVariant]]()
    executeOnPooledThread { inReadAction {
      val variants = computeVariants(project, position)
      promise.setResult(variants)
    }}
    promise
  }

  @NotNull
  override def computeVariants(@NotNull project: Project, @NotNull position: XSourcePosition): JList[JavaBPVariant] = {
    val emptyList = Collections.emptyList[JavaBPVariant]

    val dumbService = DumbService.getInstance(project)
    if (dumbService.isDumb) return emptyList

    val file = PsiManager.getInstance(project).findFile(position.getFile) match {
      case null => return emptyList
      case sf: ScalaFile => sf
      case _ => return emptyList
    }
    val line = position.getLine

    val lambdas = ScalaPositionManager.lambdasOnLine(file, line)
    val condRet = ScalaLineBreakpointType.findSingleConditionalReturn(file, line)
    if (lambdas.isEmpty && condRet.isEmpty) return emptyList

    val elementAtLine = SourcePosition.createFromLine(file, line).getElementAt

    var res: List[JavaBPVariant] = Nil

    val method = DebuggerUtil.getContainingMethod(elementAtLine)

    for (startMethod <- method) {
      assert(!lambdas.contains(startMethod))
      res = res :+ new ExactScalaBreakpointVariant(position, startMethod, -1)
    }

    if (lambdas.nonEmpty) {
      res = new JavaBreakpointVariant(position, lambdas.size) +: res //adding all variants at the beginning
      for ((lambda, ordinal) <- lambdas.zipWithIndex) {
        res = res :+ new ExactScalaBreakpointVariant(XSourcePositionImpl.createByElement(lambda), lambda, ordinal)
      }
    }

    for (elem <- condRet) {
      val lambdaOrdinal = DebuggerUtil.getContainingMethod(elem).map(lambdas.indexOf).getOrElse(-1)
      res = res :+ new ConditionalReturnJavaBreakpointVariant(position, elem, lambdaOrdinal)
    }

    res.asJava
  }

  override def matchesPosition(@NotNull breakpoint: LineBreakpoint[_], @NotNull position: SourcePosition): Boolean = {
    val method = getContainingMethod(breakpoint)
    if (method == null) return false

    if (!breakpoint.isInstanceOf[RunToCursorBreakpoint] && isMatchAll(breakpoint)) return true

    if (isLambda(breakpoint)) {
      ScalaDebuggerUsagesCollector.logLambdaBreakpoint(breakpoint.getProject)
    }

    DebuggerUtil.inTheMethod(position, method)
  }

  @Nullable
  override def getContainingMethod(@NotNull breakpoint: LineBreakpoint[_]): PsiElement = {
    val position: SourcePosition = breakpoint.getSourcePosition
    if (position == null || position.getElementAt == null) return null

    val ordinal = lambdaOrdinal(breakpoint)
    val lambdas = ScalaPositionManager.lambdasOnLine(position.getFile, position.getLine)
    if (!isLambda(breakpoint) || ordinal > lambdas.size - 1)
      DebuggerUtil.getContainingMethod(position.getElementAt).orNull
    else lambdas(ordinal)
  }

  override def getHighlightRange(breakpoint: XLineBreakpoint[JavaLineBreakpointProperties]): TextRange = {
    BreakpointManager.getJavaBreakpoint(breakpoint) match {
      case lineBp: LineBreakpoint[_] =>
        val condRet = breakpoint.getProperties.isConditionalReturn
        val lambda = !condRet && isLambda(lineBp)

        val dumbService = DumbService.getInstance(lineBp.getProject)
        val highlightedElement = if (dumbService.isDumb) {
          breakpoint match {
            case breakpointImpl: XLineBreakpointImpl[_] =>
              dumbService.smartInvokeLater { () =>
                executeOnPooledThread {
                  if (lambda && lineBp.isValid) {
                    inReadAction(getContainingMethod(lineBp)) //populating caches outside edt
                  }
                  invokeLater {
                    if (breakpointImpl.isValid) {
                      breakpointImpl.getHighlighter.dispose()
                      breakpointImpl.updateUI()
                    }
                  }
                }
              }
            case _ =>
          }
          null
        }
        else if (condRet) ScalaLineBreakpointType.findSingleConditionalReturn(lineBp.getSourcePosition).orNull
        else getContainingMethod(lineBp) ensuring lambda

        if (highlightedElement != null) highlightedElement.getTextRange else null

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

  private class ExactScalaBreakpointVariant(position: XSourcePosition, element: PsiElement, lambdaOrdinal: Integer)
    extends ExactJavaBreakpointVariant(position, element, lambdaOrdinal) {

    private val isLambda = lambdaOrdinal != null && lambdaOrdinal >= 0

    override def getIcon: Icon = {
      if (isLambda) AllIcons.Nodes.Function
      else element match {
        case e @ (_: PsiMethod | _: PsiClass | _: PsiFile) => e.getIcon(0)
        case _ => AllIcons.Debugger.Db_set_breakpoint
      }

    }

    @Nls
    override def getText: String = {
      if (isLambda) super.getText
      else {
        element match {
          case c: ScClass => DebuggerBundle.message("breakpoint.location.constructor.of", c.name)
          case ed: ScEarlyDefinitions =>
            val clazz = PsiTreeUtil.getParentOfType(ed, classOf[ScTypeDefinition])
            if (clazz != null) DebuggerBundle.message("breakpoint.location.early.definitions.of", clazz.name)
            else DebuggerBundle.message("breakpoint.location.line.in.containing.block")
          case (_: ScFunction) & (named: ScNamedElement) => DebuggerBundle.message("breakpoint.location.line.in.function", named.name)
          case _: ScalaFile => DebuggerBundle.message("breakpoint.location.line.in.containing.file")
          case _ => DebuggerBundle.message("breakpoint.location.line.in.containing.block")
        }
      }
    }

  }
}

object ScalaLineBreakpointType {

  def isReturnKeyword(element: PsiElement): Boolean =
    JavaLineBreakpointType.isReturnKeyword(element)

  def findSingleConditionalReturn(position: SourcePosition): Option[PsiElement] = {
    if (position == null) return None
    findSingleConditionalReturn(position.getFile, position.getLine)
  }

  def findSingleConditionalReturn(file: PsiFile, lineNumber: Int): Option[PsiElement] = {
    val condRet = JavaLineBreakpointType.findSingleConditionalReturn(file, lineNumber)
    if (condRet == null) return None
    DebuggerUtil.getContainingMethod(condRet) match {
      // Not yet supported because non-local transfer without `return` instruction will be used there.
      Support breakpoint on throw if there is no explicit throw in this line.
      case Some(l) if ScalaPositionManager.isLambda(l) => None
      case _ => Some(condRet)
    }
  }
}