package org.jetbrains.sbt
package language.completion

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi._
import com.intellij.util.ProcessingContext
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
 * Created by Nikolay Obedin on 7/10/14.
 */

class SbtCompletionContributor extends CompletionContributor {

  val afterInfixOperator = PlatformPatterns.psiElement().withSuperParent(2, classOf[ScInfixExpr])


  extend(CompletionType.BASIC, afterInfixOperator, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val place     = parameters.getPosition
      val parentRef = place.getParent.asInstanceOf[ScReferenceExpression]
      val operator  = parentRef.getPrevSiblingNotWhitespace.asInstanceOf[ScReferenceExpression]

      def qualifiedName(t: ScType) = ScType.extractClass(t).map(_.getQualifiedName).getOrElse("")

      // In expression `setting += ???` extracts type T of `setting: Setting[Seq[T]]`
      def extractSeqType: ScType = {
        if (operator.getText != "+=")
          return types.Nothing
        operator.getType() match {
          case Success(operatorType: ScParameterizedType, _) =>
            operatorType.typeArgs.last match {
              case ScParameterizedType(settingType, Seq(seqFullType)) if qualifiedName(settingType) == "sbt.Init.Setting" =>
                val collectionTypeNames = Seq("scala.collection.Seq", "scala.collection.immutable.Set")
                seqFullType match {
                  case ScParameterizedType(seqType, Seq(valType)) if collectionTypeNames contains qualifiedName(seqType) =>
                    valType
                  case _ => types.Nothing
                }
              case _ => types.Nothing
            }
          case _ => types.Nothing
        }
      }

      def getScopeType: ScType = {
        if (operator.getText != "in")
          return types.Nothing
        val manager = ScalaPsiManager.instance(place.getProject)
        val scopeClass = manager.getCachedClass("sbt.Scope", place.getResolveScope, ClassCategory.TYPE)
        ScDesignatorType(scopeClass)
      }

      val expectedTypes = Seq(
        parentRef.expectedType().getOrElse(types.Nothing),
        extractSeqType,
        getScopeType
      ).filter(t => t != types.Nothing && !t.isInstanceOf[NonValueType])
      val expectedType = expectedTypes.headOption.getOrElse(types.Nothing)

      if (parameters.getOriginalFile.getFileType.getName != Sbt.Name
              || expectedType == types.Nothing)
        return

      def isAccessible(cls: PsiMember): Boolean = ResolveUtils.isAccessible(cls, place, forCompletion=true)

      def collectAndApplyVariants(_obj: PsiClass): Unit = _obj match {
        case obj: ScObject if ResolveUtils.isAccessible(obj, place, forCompletion = true) && ScalaPsiUtil.hasStablePath(obj) =>
          def fetchLookup(element: ScTypedDefinition) {
            val lookup = LookupElementManager.getLookupElement(new ScalaResolveResult(element), isClassName = true,
              isOverloadedForClassName = false, shouldImport = true, isInStableCodeReference = false).apply(0)
            lookup.addLookupStrings(obj.name + "." + element.name)
            applyVariant(lookup)
          }
          obj.members.foreach {
            case v: ScValue    => v.declaredElements foreach fetchLookup
            case v: ScVariable => v.declaredElements foreach fetchLookup
            case obj: ScObject => fetchLookup(obj)
            case _ => // do nothing
          }
        case _ => // do nothing
      }

      def applyVariant(_variant: Object) {
        def apply(item: ScalaLookupItem) {
          item.isSbtLookupItem = true
          result.addElement(item)
        }
        val variant = _variant match {
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

      // Get results from companion objects and all that stuff
      ScType.extractClass(expectedType) match {
        case Some(clazz: ScTypeDefinition) =>
          expectedType match {
            case ScProjectionType(proj, _: ScTypeAlias | _: ScClass | _: ScTrait, _) =>
              ScType.extractClass(proj) foreach (cls => {
                if (isAccessible(cls) && ScalaPsiUtil.hasStablePath(cls))
                  collectAndApplyVariants(cls)
              })
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
