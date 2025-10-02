package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection._
import com.intellij.java.i18n.JavaI18nBundle
import com.intellij.psi.{util => _, _}
import org.jetbrains.plugins.scala.annotator.element.ScMethodInvocationAnnotator
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScParenthesizedElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

//noinspection InstanceOf
class ScalaInvalidPropertyKeyInspection extends LocalInspectionTool {


  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case invoc@MethodInvocation(_, Seq(ScParenthesizedElement.InnermostElement(stringLit: ScStringLiteral), restArgs @ _*)) =>
      // check the property reference and the number of parameters passed
      val key = stringLit.getValue
      val resolver = ScalaI18nUtil.PropertyReferenceResolver(stringLit)

      if (resolver.isPassedToPropertyKey) {
        if (!resolver.referenceIsValid) {
          val description = JavaI18nBundle.message("inspection.unresolved.property.key.reference.message", key)
          holder.registerProblem(stringLit, description, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
        } else {
          // check if the correct number of parameters was passed
          lazy val possibleParamCounts = resolver.possibleParamCounts
          val msgArgCount = restArgs.length
          def lastArgIsSeqArg = restArgs.lastOption.flatMap(_.asOptionOf[ScTypedExpression]).exists(_.isSequenceArg)

          // don't register a problem if there was already a problem with calling the method
          val methodCalledCorrectly = invoc.target.exists(_.problems.isEmpty)

          if (methodCalledCorrectly && !possibleParamCounts.contains(msgArgCount)) {
            val msgParamCount = possibleParamCounts.max
            val description = JavaI18nBundle.message("property.has.more.parameters.than.passed", key, msgParamCount, msgArgCount)
            if (msgArgCount > msgParamCount) {
              for (arg <- restArgs.iterator.drop(msgParamCount)) {
                holder.registerProblem(arg, description, ProblemHighlightType.GENERIC_ERROR)
              }
            } else if (msgArgCount < msgParamCount && !lastArgIsSeqArg) {
              val range = ScMethodInvocationAnnotator.missingArgumentsRange(invoc)
                .shiftLeft(invoc.getTextRange.getStartOffset)
              holder.registerProblem(invoc, description, ProblemHighlightType.GENERIC_ERROR, range)
            }
          }
        }
      }
    case annotation: ScAnnotation if annotation.getQualifiedName == AnnotationUtil.PROPERTY_KEY =>
      // check whether a bundle reference is invalid
      annotation.findAttributeValue(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER) match {
        case bundleNameElement: PsiElement =>
          val bundleName = ScalaI18nUtil.resolveBundleName(bundleNameElement)
          val isValidBundle = bundleName.exists { bundleName =>
            ScalaI18nUtil.propertiesFilesByBundleName(bundleName, bundleNameElement).nonEmpty
          }

          if (!isValidBundle) {
            val description = JavaI18nBundle.message("inspection.invalid.resource.bundle.reference", bundleName.getOrElse(bundleNameElement.getText))
            holder.registerProblem(bundleNameElement, description, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          }
        case _ =>
      }
    case _ =>
  }
}
