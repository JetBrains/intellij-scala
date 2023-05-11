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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.project._

abstract class BaseJavaConvertersIntention(methodName: String) extends PsiElementBaseIntentionAction {

  def targetCollections(project: Project, scope: GlobalSearchScope): Set[PsiClass]

  val alreadyConvertedPrefixes: Set[String]

  override def isAvailable(project: Project, e: Editor, element: PsiElement): Boolean = {
    Option(getTargetExpression(element)) exists {
      scExpr =>
        val scope = scExpr.getResolveScope
        def properTargetCollection = isProperTargetCollection(project, scope, scExpr.getTypeAfterImplicitConversion().tr)
        def parentNonConvertedCollection = scExpr match {
          case Parent(parent: ScExpression) => !isAlreadyConvertedCollection(parent.getTypeAfterImplicitConversion().tr)
          case _ => true
        }
        properTargetCollection && parentNonConvertedCollection
    }
  }

  private def isProperTargetCollection(project: Project, scope: GlobalSearchScope, typeResult: TypeResult): Boolean =
    typeResult.exists {
      scType =>
        scType.extractClass exists {
          psiClass =>
            val colls = targetCollections(project, scope)
            colls.exists(cls => psiClass == cls || psiClass.isInheritor(cls, true))
        }
    }

  private def isAlreadyConvertedCollection(typeResult: TypeResult): Boolean =
    typeResult.exists {
      scType =>
        scType.extractClass exists {
          psiClass => alreadyConvertedPrefixes.exists(prefix => psiClass.qualifiedName.startsWith(prefix))
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
      val replacement = createExpressionFromText(s"${expression.getText}.$methodName", expression)(expression.getManager)
      CodeEditUtil.replaceChild(expression.getParent.getNode, expression.getNode, replacement.getNode)
    }
    IntentionPreviewUtils.write { () =>
      addImport()
      appendAsMethod()
    }
  }

  protected def getTargetExpression(element: PsiElement): ScExpression =
    PsiTreeUtil.getNonStrictParentOfType(element, classOf[ScExpression])
}
