package org.jetbrains.sbt
package language.completion

import com.intellij.codeInsight.completion._
import com.intellij.patterns.{PlatformPatterns, PsiElementPattern}
import com.intellij.psi._
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.{ScalaChainLookupElement, ScalaLookupItem}
import org.jetbrains.plugins.scala.lang.completion.{ScalaCompletionContributor, positionFromParameters}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @author Nikolay Obedin
 * @since 7/10/14.
 */

class SbtCompletionContributor extends ScalaCompletionContributor {

  val afterInfixOperator: PsiElementPattern.Capture[PsiElement] = PlatformPatterns.psiElement().withSuperParent(2, classOf[ScInfixExpr])


  extend(CompletionType.BASIC, afterInfixOperator, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      if (parameters.getOriginalFile.getFileType.getName != Sbt.Name) return
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
        if (operator.getText != "+=") return None
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
        if (operator.getText != "in") return None
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
        case Seq(t, rest @ _*) => t
        case _ => return
      }

      def isAccessible(cls: PsiMember): Boolean = ResolveUtils.isAccessible(cls, place, forCompletion = true)

      // Collect all values, variables and inner objects from given object amd apply them
      def collectAndApplyVariants(obj: PsiClass): Unit = obj match {
        case obj: ScObject if isAccessible(obj) && ScalaPsiUtil.hasStablePath(obj) =>
          def fetchAndApply(element: ScTypedDefinition) {
            val lookup = new ScalaResolveResult(element)
              .getLookupElement(isClassName = true, shouldImport = true)
              .head
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
              proj.extractClass foreach collectAndApplyVariants
            case _ => // do nothing
          }
          ScalaPsiUtil.getCompanionModule(clazz) foreach collectAndApplyVariants
        case Some(p: PsiClass) if isAccessible(p) =>
          p.getFields.foreach (field => {
            if (field.hasModifierProperty("static") && isAccessible(field)) {
              val lookup = new ScalaResolveResult(field)
                .getLookupElement(isClassName = true, shouldImport = true)
                .head
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
