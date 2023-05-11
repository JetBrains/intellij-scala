package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGenericCall, ScNewTemplateDefinition, ScParenthesisedExpr, ScReferenceExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.project._

import scala.annotation.tailrec

abstract class BaseJavaConvertersIntention(methodName: String) extends PsiElementBaseIntentionAction {

  def targetCollections(project: Project, scope: GlobalSearchScope): Set[PsiClass]

  val alreadyConvertedPrefixes: Set[String]

  override def isAvailable(project: Project, e: Editor, element: PsiElement): Boolean =
    Option(getTargetExpression(element)).exists { scExpr =>
      val scope = scExpr.getResolveScope

      def properTargetCollection = isProperTargetCollection(project, scope, scExpr.getTypeAfterImplicitConversion().tr)

      @tailrec def parentNonConvertedCollection(expr: ScExpression): Boolean = expr match {
        case Parent(parenthesised: ScParenthesisedExpr) =>
          parentNonConvertedCollection(parenthesised)
        case Parent(parent: ScExpression) =>
          !isAlreadyConvertedCollection(parent.getTypeAfterImplicitConversion().tr)
        case _ => true
      }

      properTargetCollection && parentNonConvertedCollection(scExpr)
    }

  private def isProperTargetCollection(project: Project, scope: GlobalSearchScope, typeResult: TypeResult): Boolean =
    typeResult.exists { scType =>
      scType.extractClass.exists { psiClass =>
        val colls = targetCollections(project, scope)
        colls.exists(cls => psiClass == cls || psiClass.isInheritor(cls, true))
      }
    }

  private def isAlreadyConvertedCollection(typeResult: TypeResult): Boolean =
    typeResult.exists { scType =>
      scType.extractClass.exists { psiClass =>
        alreadyConvertedPrefixes.exists(psiClass.qualifiedName.startsWith)
      }
    }

  override def invoke(p: Project, e: Editor, element: PsiElement): Unit = {
    def addImport(): Unit = {
      val importsHolder: ScImportsHolder =
        Option(PsiTreeUtil.getParentOfType(element, classOf[ScPackaging]))
          .getOrElse(element.getContainingFile.asInstanceOf[ScImportsHolder])
      val path = if (element.newCollectionsFramework) // available since 2.13
        "scala.jdk.CollectionConverters._"
      else
        "scala.collection.JavaConverters._"
      importsHolder.addImportForPath(path)
    }

    def appendAsMethod(): Unit = {
      val expression: ScExpression = getTargetExpression(element)
      // add parentheses around infix and postfix calls
      val replacementText = s"${expression.getText.parenthesize(expression.is[ScSugarCallExpr])}.$methodName"
      val replacement = createExpressionFromText(replacementText, expression)(expression.getManager)
      CodeEditUtil.replaceChild(expression.getParent.getNode, expression.getNode, replacement.getNode)
    }

    IntentionPreviewUtils.write { () =>
      addImport()
      appendAsMethod()
    }
  }

  private def getTargetExpression(element: PsiElement): ScExpression = {
    val expr = PsiTreeUtil.getNonStrictParentOfType(element, classOf[ScExpression])

    if (expr == null) expr
    else {
      val maybeTargetExpr = expr
        .withParents
        .takeWhile(_.is[ScExpression])
        .findByType[ScNewTemplateDefinition, MethodInvocation, ScGenericCall]

      maybeTargetExpr match {
        case Some(gc: ScGenericCall) =>
          // in case it is `java.util.Collections.emptyList[String]()`, include the call as well
          gc.getContext.asOptionOf[MethodInvocation].getOrElse(gc)
        case Some(e: ScExpression) => e
        case _ =>
          // if it is not a `new ...` or a method invocation:
          expr match {
            case ref: ScReferenceExpression =>
              // get the outermost reference expr: `java.util.Collect<caret>ions.emptyList` => include `emptyList`
              ref.withParents
                .takeWhile(_.is[ScReferenceExpression])
                .lastOption.get
                .asInstanceOf[ScExpression]
            case _ => expr
          }
      }
    }
  }
}
