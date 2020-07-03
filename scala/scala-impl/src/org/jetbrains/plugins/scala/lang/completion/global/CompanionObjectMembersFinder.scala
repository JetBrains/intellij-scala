package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] trait CompanionObjectMembersFinder[T <: ScTypedDefinition] {

  this: GlobalMembersFinder =>

  // todo import, class scope import setting reconsider

  override protected def candidates: Iterable[GlobalMemberResult] = for {
    ClassOrTrait(CompanionModule(targetObject)) <- findTargets

    member <- targetObject.members
    if isAccessible(member)

    namedElement <- namedElementsIn(member)
  } yield createResult(
    new ScalaResolveResult(namedElement),
    targetObject
  )

  protected def findTargets: Iterable[PsiElement]

  protected def namedElementsIn(member: ScMember): Seq[T]

  protected def createResult(resolveResult: ScalaResolveResult,
                             classToImport: ScObject): GlobalMemberResult

}

private[completion] object CompanionObjectMembersFinder {

  final class Regular(override protected val place: ScReferenceExpression,
                      accessAll: Boolean)
    extends GlobalMembersFinder(place, accessAll)
      with CompanionObjectMembersFinder[ScTypedDefinition] {

    override protected def findTargets: Iterable[PsiElement] =
      place.withContexts.toIterable

    override protected def namedElementsIn(member: ScMember): Seq[ScTypedDefinition] = member match {
      case value: ScValueOrVariable => value.declaredElements
      case function: ScFunction if !function.isConstructor => Seq(function)
      case _ => Seq.empty
    }

    override protected def createResult(resolveResult: ScalaResolveResult,
                                        classToImport: ScObject): GlobalMemberResult =
      CompanionMemberResult(resolveResult, classToImport)

    private final case class CompanionMemberResult(override val resolveResult: ScalaResolveResult,
                                                   override val classToImport: ScObject)
      extends GlobalMemberResult(resolveResult, classToImport) {

      override protected def buildItem(lookupItem: ScalaLookupItem,
                                       shouldImport: Boolean): Option[ScalaLookupItem] =
        if (shouldImport)
          super.buildItem(lookupItem, shouldImport)
        else
          None

      override protected def createInsertHandler: ScalaImportingInsertHandler =
        new ScalaImportingInsertHandler(classToImport) {

          override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
            qualifyOnly(reference)
        }
    }
  }

  final class ExtensionLike(private val originalType: ScType,
                            override protected val place: ScConstructorOwner,
                            accessAll: Boolean)
    extends GlobalMembersFinder(place, accessAll)
      with CompanionObjectMembersFinder[ScFunction] {

    override protected def findTargets: Seq[PsiClass] =
      place +: place.supers

    override protected def namedElementsIn(member: ScMember): Seq[ScFunction] = member match {
      case function: ScFunction =>
        function.parameters match {
          case Seq(head) if head.getRealParameterType.exists(originalType.conforms) =>
            Seq(function)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }

    override protected def createResult(resolveResult: ScalaResolveResult,
                                        classToImport: ScObject): GlobalMemberResult =
      ExtensionLikeCandidate(resolveResult, classToImport)

    private final case class ExtensionLikeCandidate(override val resolveResult: ScalaResolveResult,
                                                    override val classToImport: ScObject)
      extends GlobalMemberResult(resolveResult, classToImport, Some(classToImport)) {

      override protected def createInsertHandler: InsertHandler[ScalaLookupItem] =
        (context: InsertionContext, _: ScalaLookupItem) => {
          val reference@ScReferenceExpression.withQualifier(qualifier) = context
            .getFile
            .findReferenceAt(context.getStartOffset)

          val function = resolveResult
            .getElement
            .asInstanceOf[ScFunction]
          val functionName = function.name

          val ScMethodCall(methodReference: ScReferenceExpression, _) =
            replaceReference(reference, functionName + "(" + qualifier.getText + ")")

          if (function != methodReference.resolve) {
            val ScReferenceExpression.withQualifier(objectReference: ScReferenceExpression) =
              replaceReference(methodReference, classToImport.name + "." + functionName)

            objectReference.bindToElement(classToImport)
          }
        }

      private def replaceReference(reference: ScReferenceExpression,
                                   text: String) =
        reference.replaceExpression(
          createExpressionWithContextFromText(text, reference.getContext, reference),
          removeParenthesis = true
        )
    }
  }
}