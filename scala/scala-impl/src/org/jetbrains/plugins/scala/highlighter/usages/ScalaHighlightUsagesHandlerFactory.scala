package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.highlighting.{HighlightUsagesHandlerBase, HighlightUsagesHandlerFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.highlighter.usages.ScalaHighlightImplicitUsagesHandler.TargetKind
import org.jetbrains.plugins.scala.highlighter.usages.ScalaHighlightUsagesHandlerFactory.implicitHighlightingEnabled
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScEnd, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScTypeDefinition}
import org.jetbrains.plugins.scala.util.RichThreadLocal

final class ScalaHighlightUsagesHandlerFactory extends HighlightUsagesHandlerFactory {

  import ScalaTokenType._
  import ScalaTokenTypes._

  override def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[_ <: PsiElement] = {
    if (!file.is[ScalaFile]) return null
    val offset = TargetElementUtil.adjustOffset(file, editor.getDocument, editor.getCaretModel.getOffset)
    val element: PsiElement = file.findElementAt(offset) match {
      case _: PsiWhiteSpace => file.findElementAt(offset - 1)
      case elem => elem
    }
    if (element == null || element.getNode == null) return null

    element.getParent match {
      case end: ScEnd =>
        val endMarkerHandler =
          if (end.tag.isIdentifier && end.tag == element) {
            end.begin.map(_.tag).collect {
              case named: ScNamedElement =>
                ScHighlightEndMarkerUsagesHandler(named, editor, file)
              case ref: ScStableCodeReference =>
                ScHighlightEndMarkerUsagesHandler(ref, editor, file)
            }
          } else None
        return endMarkerHandler.orNull
      case _ =>
    }

    element.getNode.getElementType match {
      case `kRETURN` =>
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition])
        if (fun != null) return new ScalaHighlightExitPointsHandler(fun, editor, file, element)
      case `kDEF` =>
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunction])
        fun match {
          case d: ScFunctionDefinition => return new ScalaHighlightExitPointsHandler(d, editor, file, element)
          case _ =>
        }
      case `kVAL` =>
        PsiTreeUtil.getParentOfType(element, classOf[ScPatternDefinition]) match {
          case pattern@ScPatternDefinition.expr(expr) if pattern.isSimple =>
            return new ScalaHighlightExprResultHandler(expr, editor, file, element)
          case _ =>
        }
      case `kVAR` =>
        PsiTreeUtil.getParentOfType(element, classOf[ScVariableDefinition]) match {
          case pattern@ScVariableDefinition.expr(expr) if pattern.isSimple =>
            return new ScalaHighlightExprResultHandler(expr, editor, file, element)
          case _ =>
        }
      case `kCASE` =>
        val cc = PsiTreeUtil.getParentOfType(element, classOf[ScCaseClause])
        if (cc != null) {
          cc.expr match {
            case Some(expr) =>
              return new ScalaHighlightExprResultHandler(expr, editor, file, element)
            case _ =>
          }
        }
      case `kMATCH` =>
        val matchStmt = PsiTreeUtil.getParentOfType(element, classOf[ScMatch])
        if (matchStmt != null) {
          return new ScalaHighlightExprResultHandler(matchStmt, editor, file, element)
        }
      case `kTRY` =>
        val tryStmt = PsiTreeUtil.getParentOfType(element, classOf[ScTry])
        if (tryStmt != null) {
          return new ScalaHighlightExprResultHandler(tryStmt, editor, file, element)
        }
      case `kFOR` =>
        val forStmt = PsiTreeUtil.getParentOfType(element, classOf[ScFor])
        if (forStmt != null && forStmt.isYield) {
          forStmt.body match {
            case Some(body) =>
              return new ScalaHighlightExprResultHandler(body, editor, file, element)
            case _ =>
          }
        }
      case `kIF` =>
        val ifStmt = PsiTreeUtil.getParentOfType(element, classOf[ScIf])
        if (ifStmt != null && ifStmt.elseExpression.isDefined) {
          return new ScalaHighlightExprResultHandler(ifStmt, editor, file, element)
        }
      case `tFUNTYPE` | `tFUNTYPE_ASCII` =>
        val maybeResult = element.getParent match {
          case funcExpr: ScFunctionExpr => funcExpr.result
          case caseClause: ScCaseClause => caseClause.resultExpr
          case _ => None
        }
        maybeResult match {
          case Some(resultExpr) =>
            return new ScalaHighlightExprResultHandler(resultExpr, editor, file, element)
          case _ =>
        }
      case IsTemplateDefinition() =>
        val typeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition])
        if (typeDefinition != null) {
          return new CompanionHighlightHandler(element, typeDefinition, editor, file)
        }
      case `tIDENTIFIER` =>
        lazy val isInScala3File = element.isInScala3File

        element.getParent match {
          case enumCase: ScEnumCase => return new ScalaHighlightEnumCaseUsagesHandler(enumCase, file, editor)
          case ScConstructorInvocation.byReference(constr) => return new ScalaHighlightConstructorInvocationUsages(constr, file, editor)
          case ref@ScConstructorInvocation.byUniversalApply(_) => return new ScalaHighlightConstructorInvocationUsages(Option(ref), file, editor)
          case named: ScNamedElement => return implicitHighlighter(editor, file, named)
          case ref: ScReference =>
            if (isInScala3File) {
              //this can potentially cause freezes, but I couldn't find
              //a reliable way to do it the other way. Also, other implementations
              //of HighlightUsagesHandlerFactory seem to ebe using resovle-based logic too.
              ref.resolve() match {
                case ScEnumCase.Original(enumCase) => return new ScalaHighlightEnumCaseUsagesHandler(enumCase, file, editor)
                case _ => return implicitHighlighter(editor, file, ref)
              }
            } else return implicitHighlighter(editor, file, ref)
          case _ =>
        }

      //to highlight usages of implicit parameter from context bound
      case `tCOLON` =>
        (element.getParent, element.getNextSiblingNotWhitespaceComment) match {
          case (tp: ScTypeParam, te: ScTypeElement) => return implicitHighlighter(editor, file, (tp, te))
          case _ =>
        }
      case _ =>
    }
    null
  }

  private def implicitHighlighter[T: TargetKind](editor: Editor,
                                                 file: PsiFile,
                                                 data: T): ScalaHighlightImplicitUsagesHandler[T] =
    if (implicitHighlightingEnabled.value) new ScalaHighlightImplicitUsagesHandler(editor, file, data)
    else null

}

object ScalaHighlightUsagesHandlerFactory {
  val implicitHighlightingEnabled: RichThreadLocal[Boolean] = new RichThreadLocal(true)
}
