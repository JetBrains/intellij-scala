package org.jetbrains.plugins.scala.debugger.breakpoints

import java.util.{Collections, List => JList}
import javax.swing.Icon

import scala.collection.JavaConverters._

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.ui.breakpoints._
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.{XDebuggerUtil, XSourcePosition}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.util.UIFreezingGuard
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

/**
 * @author Nikolay.Tropin
 */
class ScalaLineBreakpointType extends JavaLineBreakpointType("scala-line", ScalaBundle.message("line.breakpoints.tab.title")) {

  override def getDisplayName: String = ScalaBundle.message("line.breakpoints.tab.title")

  override def canPutAt(@NotNull file: VirtualFile, line: Int, @NotNull project: Project): Boolean = {
    val psiFile: PsiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null || !psiFile.getLanguage.isKindOf(ScalaLanguage.INSTANCE)) {
      return false
    }
    val document: Document = FileDocumentManager.getInstance.getDocument(file)
    if (document == null) return false

    var result: Boolean = false
    val processor: Processor[PsiElement] = new Processor[PsiElement] {
      override def process(e: PsiElement): Boolean = e match {
        case ElementType(ScalaTokenTypes.kPACKAGE | ScalaTokenTypes.kIMPORT) => false
        case _: PsiWhiteSpace => true
        case _ if PsiTreeUtil.getParentOfType(e, classOf[PsiComment]) != null => true
        case _ if PsiTreeUtil.getParentOfType(e, classOf[ScExpression], classOf[ScConstructorPattern], classOf[ScInfixPattern], classOf[ScClass]) != null =>
          result = true
          false
        case _ => true
      }
    }
    XDebuggerUtil.getInstance.iterateLine(project, document, line, processor)
    result
  }

  @NotNull
  override def computeVariants(@NotNull project: Project, @NotNull position: XSourcePosition): JList[JavaLineBreakpointType#JavaBreakpointVariant] = {
    val emptyList = Collections.emptyList[JavaLineBreakpointType#JavaBreakpointVariant]

    val dumbService = DumbService.getInstance(project)
    if (dumbService.isDumb) return emptyList

    val file = PsiManager.getInstance(project).findFile(position.getFile) match {
      case null => return emptyList
      case sf: ScalaFile => sf
      case _ => return emptyList
    }
    val line = position.getLine

    val timeoutMs = 100

    val lambdas: Seq[PsiElement] = UIFreezingGuard.withTimeout[Seq[PsiElement]](timeoutMs, Nil) {
      ScalaPositionManager.lambdasOnLine(file, line)
    }
    if (lambdas.isEmpty) return emptyList

    val elementAtLine = SourcePosition.createFromLine(file, line).getElementAt

    var res: List[JavaLineBreakpointType#JavaBreakpointVariant] = List()

    val method = UIFreezingGuard.withTimeout[Option[PsiElement]](timeoutMs, None) {
      DebuggerUtil.getContainingMethod(elementAtLine)
    }

    for (startMethod <- method; if !lambdas.contains(startMethod)) {
      res = res :+ new ExactScalaBreakpointVariant(position, startMethod, -1)
    }

    for ((lambda, ordinal) <- lambdas.zipWithIndex) {
      res = res :+ new ExactScalaBreakpointVariant(XSourcePositionImpl.createByElement(lambda), lambda, ordinal)
    }

    if (res.size == 1) emptyList
    else (new JavaBreakpointVariant(position) +: res).asJava //adding all variants
  }

  override def matchesPosition(@NotNull breakpoint: LineBreakpoint[_], @NotNull position: SourcePosition): Boolean = {
    val method = getContainingMethod(breakpoint)
    if (method == null) return false

    val lambdaOrd = lambdaOrdinal(breakpoint)

    if (!breakpoint.isInstanceOf[RunToCursorBreakpoint] && lambdaOrd == null) return true

    if (isScalaLambda(lambdaOrd, method)) {
      Stats.trigger(FeatureKey.debuggerLambdaBreakpoint)
    }

    DebuggerUtil.inTheMethod(position, method)
  }

  private def isScalaLambda(lambdaOrdinal: Integer, elem: PsiElement) = {
    lambdaOrdinal != null && lambdaOrdinal >= 0 && elem.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
  }

  @Nullable
  override def getContainingMethod(@NotNull breakpoint: LineBreakpoint[_]): PsiElement = {
    val position: SourcePosition = breakpoint.getSourcePosition
    if (position == null || position.getElementAt == null) return null

    val ordinal = lambdaOrdinal(breakpoint)
    val lambdas = ScalaPositionManager.lambdasOnLine(position.getFile, position.getLine)
    if (ordinal == null || ordinal == -1 || ordinal > lambdas.size - 1)
      DebuggerUtil.getContainingMethod(position.getElementAt).orNull
    else lambdas(ordinal)
  }

  override def getHighlightRange(breakpoint: XLineBreakpoint[JavaLineBreakpointProperties]): TextRange = {
    BreakpointManager.getJavaBreakpoint(breakpoint) match {
      case lineBp: LineBreakpoint[_] if lambdaOrdinal(lineBp) != null =>
        val dumbService = DumbService.getInstance(lineBp.getProject)
        if (dumbService.isDumb) {
          breakpoint match {
            case breakpointImpl: XLineBreakpointImpl[_] =>
              dumbService.smartInvokeLater {
                executeOnPooledThread {
                  if (lineBp.isValid) {
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
        else Option(getContainingMethod(lineBp)).map(_.getTextRange).orNull
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

    override def getText: String = {
      if (isLambda) super.getText
      else {
        element match {
          case c: ScClass => s"constructor of ${c.name}"
          case ed: ScEarlyDefinitions =>
            val clazz = PsiTreeUtil.getParentOfType(ed, classOf[ScTypeDefinition])
            if (clazz != null) s"early definitions of ${clazz.name}"
            else "line in containing block"
          case (_: ScFunction) && (named: ScNamedElement) => s"line in function ${named.name}"
          case _: ScalaFile => "line in containing file"
          case _ => "line in containing block"
        }
      }
    }

  }
}
