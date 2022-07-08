package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
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

import scala.annotation.tailrec

abstract class BaseJavaConvertersIntention(methodName: String) extends PsiElementBaseIntentionAction {

  val targetCollections: Set[String]

  val alreadyConvertedPrefixes: Set[String]

  override def isAvailable(p: Project, e: Editor, element: PsiElement): Boolean = {
    Option(getTargetExpression(element)) exists {
      scExpr =>
        def properTargetCollection = isProperTargetCollection(scExpr.getTypeAfterImplicitConversion().tr)
        def parentNonConvertedCollection = scExpr match {
          case Parent(parent: ScExpression) => !isAlreadyConvertedCollection(parent.getTypeAfterImplicitConversion().tr)
          case _ => true
        }
        properTargetCollection && parentNonConvertedCollection
    }
  }

  private def isProperTargetCollection(typeResult: TypeResult): Boolean =
    typeResult.exists {
      scType =>
        scType.extractClass exists {
          psiClass =>
            val superNames: Set[String] = allSupers(psiClass)
            superNames.exists(i => targetCollections.contains(i))
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
      val replacement = createExpressionFromText(s"${expression.getText}.$methodName")(expression.getManager)
      CodeEditUtil.replaceChild(expression.getParent.getNode, expression.getNode, replacement.getNode)
    }
    inWriteAction {
      addImport()
      appendAsMethod()
    }
  }

  protected def getTargetExpression(element: PsiElement): ScExpression =
    PsiTreeUtil.getNonStrictParentOfType(element, classOf[ScExpression])

  protected def allSupers(psiClass: PsiClass): Set[String] = {
    @tailrec
    def allSuperNames(pClasses: List[PsiClass], superNames: Set[String] = Set.empty): Set[String] = {
      pClasses match {
        case Nil => superNames
        case head :: tail => allSuperNames(head.getSupers.toList ::: tail, superNames + head.qualifiedName)
      }
    }
    allSuperNames(List(psiClass))
  }
}
