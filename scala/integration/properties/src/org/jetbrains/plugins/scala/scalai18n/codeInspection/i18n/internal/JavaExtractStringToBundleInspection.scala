package org.jetbrains.plugins.scala
package scalai18n
package codeInspection
package i18n
package internal

import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.codeInsight.intention.AbstractIntention
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal.JavaExtractStringToBundleInspection._
import BundleExtraction._

import scala.annotation.tailrec

//noinspection ScalaExtractStringToBundle
class JavaExtractStringToBundleInspection extends AbstractIntention(
  "Extract to Bundle",
  "Extract Java String to bundle in Intellj Scala Plugin"
)(
  (project, _) => {
    case MostUpperStringExpression(e, parts) =>
      executeBundleExtraction(e, parts, project) {
        case BundleExtractionInfo(bundleClassName, bundleQualifiedClassName, key, arguments) =>
          val importsHolder: PsiImportHolder = e.getContainingFile.asInstanceOf[PsiJavaFile]
          JavaPsiFacade.getInstance(project)
            .findClass(bundleQualifiedClassName, GlobalSearchScope.projectScope(project))
            .nullSafe
            .foreach(importsHolder.importClass)

          // replace string with message call
          val argString =
            if (arguments.isEmpty) ""
            else arguments.mkString(", ", ", ", "")
          e.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(
            s"""$bundleClassName.message("$key"$argString)""",
            null
          ))
      }
  }
) {
  override def startInWriteAction = false

  // Only show in our project
  override def checkFile(file: PsiFile): Boolean =
    super.checkFile(file) && (
      file.getProject.getName == "scalaUltimate" ||
        file.getProject.getName == "scalaCommunity"
    )
}

object JavaExtractStringToBundleInspection {
  object JavaString {
    def unapply(lit: PsiLiteralExpression): Option[String] =
      lit.getValue.asOptionOf[String]
  }

  object MostUpperStringExpression {
    def unapply(e: PsiElement): Option[(PsiExpression, Seq[ExtractPart])] = e match {
      case Parent(e@JavaString(_)) =>
        val mostUpper = findMostUpper(e)
        Some(mostUpper -> extractParts(mostUpper))
      case _ => None
    }

    @tailrec
    private def findMostUpper(expr: PsiExpression): PsiExpression = expr.getParent match {
      case op@JavaPlusOp(_, _) => findMostUpper(op)
      case _ => expr
    }

    private def extractParts(expr: PsiExpression): Seq[ExtractPart] = expr match {
      case JavaPlusOp(left, right) => extractParts(left) ++ extractParts(right)
      case JavaString(str) => Seq(TextPart(str))
      case expr => Seq(ExprPart(expr.getText))
    }

    private object JavaPlusOp {
      def unapply(bin: PsiBinaryExpression): Option[(PsiExpression, PsiExpression)] =
        bin.getOperationSign.textMatches("+").option(bin.getLOperand -> bin.getROperand)
    }
  }
}