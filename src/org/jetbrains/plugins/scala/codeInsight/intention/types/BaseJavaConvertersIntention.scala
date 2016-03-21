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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec

/**
 * @author Eugene Platonov
 *         23/07/13
 */
abstract class BaseJavaConvertersIntention(methodName: String) extends PsiElementBaseIntentionAction {

  val targetCollections: Set[String]

  val alreadyConvertedPrefixes: Set[String]

  def isAvailable(p: Project, e: Editor, element: PsiElement): Boolean = {
    implicit val typeSystem = p.typeSystem
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

  def isProperTargetCollection(typeResult: TypeResult[ScType])
                              (implicit typeSystem: TypeSystem): Boolean =
    typeResult.exists {
      scType =>
        scType.extractClass() exists {
          psiClass =>
            val superNames: Set[String] = allSupers(psiClass)
            superNames.exists(i => targetCollections.contains(i))
        }
    }

  def isAlreadyConvertedCollection(typeResult: TypeResult[ScType])
                                  (implicit typeSystem: TypeSystem): Boolean =
    typeResult.exists {
      scType =>
        scType.extractClass() exists {
          psiClass => alreadyConvertedPrefixes.exists(prefix => psiClass.getQualifiedName.startsWith(prefix))
        }
    }

  def invoke(p: Project, e: Editor, element: PsiElement) {
    def addImport() {
      val importsHolder: ScImportsHolder = Option(PsiTreeUtil.getParentOfType(element, classOf[ScPackaging])).
              getOrElse(element.getContainingFile.asInstanceOf[ScImportsHolder])
      val path = "scala.collection.JavaConverters._"
      importsHolder.addImportForPath(path)
    }
    def appendAsMethod() {
      val expression: ScExpression = getTargetExpression(element)
      val replacement = ScalaPsiElementFactory.createExpressionFromText(s"${expression.getText}.$methodName", expression.getManager)
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
        case head :: tail => allSuperNames(head.getSupers.toList ::: tail, superNames + head.getQualifiedName)
      }
    }
    allSuperNames(List(psiClass))
  }
}
