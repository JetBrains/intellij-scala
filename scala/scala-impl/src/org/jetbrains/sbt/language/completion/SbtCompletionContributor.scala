package org.jetbrains.sbt
package language
package completion

import com.intellij.codeInsight.completion._
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion._
import org.jetbrains.plugins.scala.lang.completion.lookups.{ScalaChainLookupElement, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{getCompanionModule, hasStablePath}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.project.ProjectContext

final class SbtCompletionContributor extends ScalaCompletionContributor {

  private val afterInfixOperator = sbtFilePattern && infixExpressionChildPattern

  extend(CompletionType.BASIC, afterInfixOperator, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      implicit val project: ProjectContext = parameters.getPosition.getProject

      val place     = positionFromParameters(parameters)
      val infixExpr = place.getContext.getContext.asInstanceOf[ScInfixExpr]
      val operator  = infixExpr.operation
      val parentRef = infixExpr.right match {
        case ref: ScReferenceExpression => ref
        case _ => return
      }

      // Check if we're on the right side of expression
      if (parentRef != place.getContext) return

      def qualifiedName(t: ScType) = t.extractClass.map(_.qualifiedName).getOrElse("")

      // In expression `setting += ???` extracts type T of `setting: Setting[Seq[T]]`
      def extractSeqType: Option[ScType] = {
        if (!operator.textMatches("+=")) return None
        operator.`type`() match {
          case Right(ParameterizedType(_, typeArgs)) =>
            typeArgs.last match {
              case ParameterizedType(settingType, Seq(seqFullType)) if qualifiedName(settingType) == "sbt.Init.Setting" =>
                val collectionTypeNames = Seq("scala.collection.Seq", "scala.collection.immutable.Set")
                seqFullType match {
                  case ParameterizedType(seqType, Seq(valType)) if collectionTypeNames contains qualifiedName(seqType) =>
                    Some(valType)
                  case _ => None
                }
              case _ => None
            }
          case _ => None
        }
      }

      def getScopeType: Option[ScType] = {
        if (!operator.textMatches("in")) return None
        place.elementScope.getCachedClass("sbt.Scope").map {
          ScDesignatorType(_)
        }
      }

      val refType = parentRef.expectedType()
        .filterNot(_.isInstanceOf[NonValueType])

      val expectedTypes = Seq(
        refType,
        extractSeqType,
        getScopeType
      ).flatten
      val expectedType = expectedTypes match {
        case Seq(t, _*) => t
        case _ => return
      }

      def isAccessible(cls: PsiMember): Boolean = ResolveUtils.isAccessible(cls, place, forCompletion = true)

      // Collect all values, variables and inner objects from given object amd apply them
      def collectAndApplyVariants(containingClass: PsiClass): Unit = containingClass match {
        case containingClass: ScObject if isAccessible(containingClass) && hasStablePath(containingClass) =>
          containingClass.membersWithSynthetic.flatMap {
            case v: ScValueOrVariable => v.declaredElements
            case o: ScObject => Seq(o)
            case _ => Seq.empty
          }.map {
            createLookupElementWithPrefix(_, containingClass)
          }.foreach {
            applyVariant(_)
          }
        case _ => // do nothing
      }

      def applyVariant(variantObj: Object): Unit = {
        def apply(item: ScalaLookupItem): Unit = {
          item.isSbtLookupItem = true
          result.addElement(item)
        }
        val variant = variantObj match {
          case el: ScalaLookupItem => el
          case ch: ScalaChainLookupElement => ch.getDelegate
          case _ => return
        }

        variant.getPsiElement match {
          case f: PsiField if f.getType.toScType().conforms(expectedType) =>
            apply(variant)
          case typed: ScTypedDefinition if typed.`type`().getOrAny.conforms(expectedType) =>
            variant.isLocalVariable =
              (typed.isVar || typed.isVal) &&
                typed.containingFile.exists(_.getName == parameters.getOriginalFile.getName)
            apply(variant)
          case _ => // do nothing
        }
      }

      // Get results from companion objects and static fields from java classes/enums
      expectedType.extractClass match {
        case Some(clazz: ScTypeDefinition) =>
          expectedType match {
            case ScProjectionType(proj, _: ScTypeAlias | _: ScClass | _: ScTrait) =>
              proj.extractClass.foreach(collectAndApplyVariants)
            case _ => // do nothing
          }
          getCompanionModule(clazz).foreach(collectAndApplyVariants)
        case Some(containingClass: PsiClass) if isAccessible(containingClass) =>
          for {
            field <- containingClass.getFields
            if field.hasModifierProperty(PsiModifier.STATIC) &&
              isAccessible(field)

            variant = createLookupElementWithPrefix(field, containingClass)
          } applyVariant(variant)
        case _ => // do nothing
      }

      // Get results from parent reference
      parentRef.getVariants.foreach(applyVariant)
    }
  })
}
