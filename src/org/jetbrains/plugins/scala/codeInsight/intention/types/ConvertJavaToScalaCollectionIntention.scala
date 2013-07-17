package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.psi.util.PsiTreeUtil
import scala.collection.JavaConverters
import scala.reflect.runtime.universe._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import org.jetbrains.plugins.scala.ScalaBundle
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.extensions._

/**
 * Converts expression representing [[java.util.Collection]] or [[java.util.Map]] to
 * scala equivalent using [[scala.collection.JavaConverters]]
 *
 * @author Eugene Platonov
 *         04/07/13
 */
class ConvertJavaToScalaCollectionIntention extends PsiElementBaseIntentionAction {
  override def getText = ScalaBundle.message("convert.java.to.scala.collection.hint")

  def getFamilyName = ConvertJavaToScalaCollectionIntention.getFamilyName

  def invoke(p: Project, e: Editor, element: PsiElement) {
    def addImport() {
      val importsHolder: ScImportsHolder = Option(PsiTreeUtil.getParentOfType(element, classOf[ScPackaging])).
              getOrElse(element.getContainingFile.asInstanceOf[ScImportsHolder])
      val fullName = typeOf[JavaConverters.type].termSymbol.fullName

      importsHolder.addImportForPath(s"$fullName._")
    }

    def appendAsScala() {
      val expression: ScExpression = getTargetExpression(element)
      val replacement = ScalaPsiElementFactory.createExpressionFromText(s"${expression.getText}.asScala", expression.getManager)
      CodeEditUtil.replaceChild(expression.getParent.getNode, expression.getNode, replacement.getNode)
    }
    inWriteAction {
      addImport()
      appendAsScala()
    }
  }

  def isAvailable(p: Project, e: Editor, element: PsiElement): Boolean =
    Option(getTargetExpression(element)) exists {
      scExpr =>
        val javaCollection = isJavaCollection(scExpr.getTypeAfterImplicitConversion().tr)
        val parentNonScalaCollection = scExpr match {
          case Parent(parent: ScExpression) => !isScalaCollection(parent.getTypeAfterImplicitConversion().tr)
          case _ => true
        }
        javaCollection && parentNonScalaCollection
    }

  private def getTargetExpression(element: PsiElement): ScExpression =
    PsiTreeUtil.getNonStrictParentOfType(element, classOf[ScExpression])

  private def isJavaCollection(typeResult: TypeResult[ScType]): Boolean =
    typeResult.exists {
      scType => ScType.extractClass(scType) exists {
        psiClass => allInterfaces(psiClass).
                exists(i => i.getQualifiedName == "java.util.Collection" || i.getQualifiedName == "java.util.Map")
      }
    }

  private def isScalaCollection(typeResult: TypeResult[ScType]): Boolean =
    typeResult.exists {
      scType => ScType.extractClass(scType) exists {
        psiClass => psiClass.getQualifiedName.startsWith("scala.collection")
      }
    }

  private def allInterfaces(psiClass: PsiClass): Set[PsiClass] = {
    @tailrec
    def allInterfaces0(pClasses: List[PsiClass], parentInterfaces: Set[PsiClass] = Set.empty): Set[PsiClass] = {
      pClasses match {
        case Nil => parentInterfaces
        case head :: tail => allInterfaces0(head.getInterfaces.toList ::: tail, parentInterfaces + head)
      }
    }
    allInterfaces0(psiClass.getInterfaces.toList)
  }

}

object ConvertJavaToScalaCollectionIntention {
  def getFamilyName = ScalaBundle.message("convert.java.to.scala.collection.name")
}