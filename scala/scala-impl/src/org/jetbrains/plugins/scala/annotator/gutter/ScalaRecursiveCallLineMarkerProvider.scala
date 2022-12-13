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

  import ScalaRecursiveCallLineMarkerProvider.{PossibleMethodCall, createLineMarkerInfo}

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_] = null // do nothing

  override def collectSlowLineMarkers(elements: util.List[_ <: PsiElement],
                                      result: util.Collection[_ >: LineMarkerInfo[_]]): Unit = {
    if (!GutterUtil.RecursionOption.isEnabled) return

    val visited = mutable.HashSet.empty[PsiElement]
    elements.forEach { element =>
      ProgressManager.checkCanceled()
      element match {
        case PossibleMethodCall(ref) =>
          val callee = ref.parents.collectFirst {
            case fn: ScFunctionDefinition if fn.recursiveReferences.contains(ref) =>
              fn
          }

          callee
            .filterNot(visited.contains)
            .foreach { fn =>
              visited.add(fn)
              result.add(createLineMarkerInfo(element))
            }
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
