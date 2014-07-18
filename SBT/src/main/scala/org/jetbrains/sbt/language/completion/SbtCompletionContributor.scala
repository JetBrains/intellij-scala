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
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.psi.types.{ScProjectionType, ScType}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

/**
 * Created by Nikolay Obedin on 7/10/14.
 */

class SbtCompletionContributor extends CompletionContributor {

  val afterInfixOperator = PlatformPatterns.psiElement().withSuperParent(2, classOf[ScInfixExpr])

  extend(CompletionType.BASIC, afterInfixOperator, new CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val place        = parameters.getPosition
      val parentRef    = place.getParent.asInstanceOf[ScReferenceExpression]
      val expectedType = parentRef.expectedType().getOrElse(types.Nothing)

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
          // FIXME: remove before releasing
          println(item)
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
            variant.isVariable = typed.isVar || typed.isVal
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

  // FIXME: Remove this before releasing
  override def fillCompletionVariants(params: CompletionParameters, results: CompletionResultSet) =
    super.fillCompletionVariants(params, results)
}
