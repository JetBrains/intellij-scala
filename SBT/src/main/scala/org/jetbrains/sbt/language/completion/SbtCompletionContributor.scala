package org.jetbrains.sbt
package language.completion

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.toPsiClassExt
import org.jetbrains.plugins.scala.lang.completion.lookups.{LookupElementManager, ScalaChainLookupElement, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager.ClassCategory
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScParameterizedType, ScProjectionType, ScType}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

/**
 * @author Nikolay Obedin
 * @since 7/10/14.
 */

class SbtCompletionContributor extends CompletionContributor {

  val afterInfixOperator = PlatformPatterns.psiElement().withSuperParent(2, classOf[ScInfixExpr])


  extend(CompletionType.BASIC, afterInfixOperator, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      if (parameters.getOriginalFile.getFileType.getName != Sbt.Name) return

      val place     = parameters.getPosition
      val infixExpr = place.getParent.getParent.asInstanceOf[ScInfixExpr]
      val operator  = infixExpr.operation
      val parentRef = infixExpr.rOp match {
        case ref: ScReferenceExpression => ref
        case _ => return
      }

      // Check if we're on the right side of expression
      if (parentRef != place.getParent) return

      def qualifiedName(t: ScType) = ScType.extractClass(t).map(_.qualifiedName).getOrElse("")

      // In expression `setting += ???` extracts type T of `setting: Setting[Seq[T]]`
      def extractSeqType: Option[ScType] = {
        if (operator.getText != "+=") return None
        operator.getType() match {
          case Success(ScParameterizedType(_, typeArgs), _) =>
            typeArgs.last match {
              case ScParameterizedType(settingType, Seq(seqFullType)) if qualifiedName(settingType) == "sbt.Init.Setting" =>
                val collectionTypeNames = Seq("scala.collection.Seq", "scala.collection.immutable.Set")
                seqFullType match {
                  case ScParameterizedType(seqType, Seq(valType)) if collectionTypeNames contains qualifiedName(seqType) =>
                    Some(valType)
                  case _ => None
                }
              case _ => None
            }
          case _ => None
        }
      }

      def getScopeType: Option[ScType] = {
        if (operator.getText != "in") return None
        val manager = ScalaPsiManager.instance(place.getProject)
        val scopeClass = manager.getCachedClass("sbt.Scope", place.getResolveScope, ClassCategory.TYPE)
        if (scopeClass != null) Some(ScDesignatorType(scopeClass)) else None
      }

      val expectedTypes = Seq(
        parentRef.expectedType().filterNot(_.isInstanceOf[NonValueType]),
        extractSeqType,
        getScopeType
      ).flatten
      val expectedType = expectedTypes match {
        case Seq(t, rest @ _*) => t
        case _ => return
      }

      def isAccessible(cls: PsiMember): Boolean = ResolveUtils.isAccessible(cls, place, forCompletion = true)

      // Collect all values, variables and inner objects from given object amd apply them
      def collectAndApplyVariants(obj: PsiClass): Unit = obj match {
        case obj: ScObject if isAccessible(obj) && ScalaPsiUtil.hasStablePath(obj) =>
          def fetchAndApply(element: ScTypedDefinition) {
            val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(element), isClassName = true,
              isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0)
            lookup.addLookupStrings(obj.name + "." + element.name)
            applyVariant(lookup)
          }
          obj.members.foreach {
            case v: ScValue    => v.declaredElements foreach fetchAndApply
            case v: ScVariable => v.declaredElements foreach fetchAndApply
            case obj: ScObject => fetchAndApply(obj)
            case _ => // do nothing
          }
        case _ => // do nothing
      }

      def applyVariant(variantObj: Object) {
        def apply(item: ScalaLookupItem) {
          item.isSbtLookupItem = true
          result.addElement(item)
        }
        val variant = variantObj match {
          case el: ScalaLookupItem => el
          case ch: ScalaChainLookupElement => ch.element
          case _ => return
        }
        variant.element match {
          case f: PsiField if ScType.create(f.getType, f.getProject, parentRef.getResolveScope).conforms(expectedType) =>
            apply(variant)
          case typed: ScTypedDefinition if typed.getType().getOrAny.conforms(expectedType) =>
            variant.isLocalVariable =
              (typed.isVar || typed.isVal) &&
              (typed.containingFile exists (_.getName == parameters.getOriginalFile.getName))
            apply(variant)
          case _ => // do nothing
        }
      }

      // Get results from companion objects and static fields from java classes/enums
      ScType.extractClass(expectedType) match {
        case Some(clazz: ScTypeDefinition) =>
          expectedType match {
            case ScProjectionType(proj, _: ScTypeAlias | _: ScClass | _: ScTrait, _) =>
              ScType.extractClass(proj) foreach collectAndApplyVariants
            case _ => // do nothing
          }
          ScalaPsiUtil.getCompanionModule(clazz) foreach collectAndApplyVariants
        case Some(p: PsiClass) if isAccessible(p) =>
          p.getFields.foreach (field => {
            if (field.hasModifierProperty("static") && isAccessible(field)) {
              val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(field), isClassName = true,
                isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0)
              lookup.addLookupStrings(p.getName + "." + field.getName)
              applyVariant(lookup)
            }
          })
        case _ => // do nothing
      }

      // Get results from parent reference
      parentRef.getVariants() foreach applyVariant
    }
  })
}
