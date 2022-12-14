package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.{LineMarkerInfo, LineMarkerProvider}
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ChildOf, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScFunctionDefinitionExt}

import java.util
import scala.collection.mutable

class ScalaRecursiveCallLineMarkerProvider extends LineMarkerProvider {

  import ScalaRecursiveCallLineMarkerProvider.{PossibleMethodCall, createLineMarkerInfo, isRecursiveCall}

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_] = null // do nothing

  override def collectSlowLineMarkers(elements: util.List[_ <: PsiElement],
                                      result: util.Collection[_ >: LineMarkerInfo[_]]): Unit = {
    if (!GutterUtil.RecursionOption.isEnabled) return

    val visitedLines = mutable.HashSet.empty[Int]
    elements.forEach { element =>
      ProgressManager.checkCanceled()
      val lineNumber = element.getLineNumber
      element match {
        case PossibleMethodCall(ref) if !visitedLines(lineNumber) && isRecursiveCall(ref) =>
          visitedLines.add(lineNumber)
          result.add(createLineMarkerInfo(element))
        case _ =>
      }
    }
  }
}

object ScalaRecursiveCallLineMarkerProvider {
  private def createLineMarkerInfo(element: PsiElement): LineMarkerInfo[PsiElement] = {
    val tooltip = ScalaBundle.message("call.is.recursive")

    new LineMarkerInfo(
      element,
      element.getTextRange,
      AllIcons.Gutter.RecursiveMethod,
      (_: PsiElement) => tooltip,
      null, // navigation handler
      GutterIconRenderer.Alignment.LEFT,
      () => tooltip
    )
  }

  private def isRecursiveCall(ref: ScReference): Boolean = ref.parents.exists {
    case fn: ScFunctionDefinition => fn.recursiveReferences.contains(ref)
    case _ => false
  }

  private object PossibleMethodCall {
    def unapply(leaf: PsiElement): Option[ScReference] = {
      if (leaf.elementType != ScalaTokenTypes.tIDENTIFIER) None
      else leaf.getParent match {
        case ref: ScStableCodeReference =>
          ref.getParent match {
            case ChildOf(_: ScConstructorInvocation) |
                 ScConstructorPattern(`ref`, _) |
                 ScInfixPattern(_, `ref`, _) =>
              Some(ref)
            case _ => None
          }
        case ref: ScReferenceExpression => Some(ref)
        case _ => None
      }
    }
  }
}
