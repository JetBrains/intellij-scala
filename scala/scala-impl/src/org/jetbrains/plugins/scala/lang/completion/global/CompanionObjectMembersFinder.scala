package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] sealed abstract class CompanionObjectMembersFinder[E <: ScalaPsiElement](override protected val place: E,
                                                                                             accessAll: Boolean)
                                                                                            (override protected val namePredicate: NamePredicate)
  extends GlobalMembersFinderBase(place, accessAll)(namePredicate) {

  // todo import, class scope import setting reconsider

  protected type T <: ScTypedDefinition

  override protected final def candidates: Iterable[GlobalMemberResult] = for {
    ClassOrTrait(CompanionModule(targetObject)) <- findTargets(place)

    member <- targetObject.members
    if isAccessible(member)

    namedElement <- namedElementsIn(member)
    if namePredicate(namedElement.name)
  } yield createResult(namedElement, targetObject)

  protected def findTargets(place: E): Iterable[PsiElement]

  protected def namedElementsIn(member: ScMember): Seq[T]

  protected def createResult(namedElement: T,
                             `object`: ScObject): CompanionObjectMemberResult

  protected sealed abstract class CompanionObjectMemberResult(protected val member: T,
                                                              protected val `object`: ScObject)
    extends GlobalMemberResult(
      new ScalaResolveResult(member),
      `object`,
      Some(`object`)
    ) {

    override def equals(other: Any): Boolean = other match {
      case that: CompanionObjectMemberResult if getClass == that.getClass =>
        member == that.member &&
          `object` == that.`object`
      case _ => false
    }

    override def hashCode: Int =
      31 * member.hashCode + `object`.hashCode

    override def toString: String =
      s"CompanionObjectMemberResult($member, ${`object`})"
  }
}

private[completion] object CompanionObjectMembersFinder {

  final class Regular(override protected val place: ScReferenceExpression,
                      accessAll: Boolean)
                     (override protected val namePredicate: NamePredicate)
    extends CompanionObjectMembersFinder(place, accessAll)(namePredicate) {

    override protected type T = ScTypedDefinition

    override protected def findTargets(place: ScReferenceExpression): Iterable[PsiElement] =
      place.withContexts.toIterable

    override protected def namedElementsIn(member: ScMember): Seq[ScTypedDefinition] = member match {
      case value: ScValueOrVariable => value.declaredElements
      case function: ScFunction if !function.isConstructor => Seq(function)
      case _ => Seq.empty
    }

    override protected def createResult(member: ScTypedDefinition,
                                        `object`: ScObject): CompanionObjectMemberResult =
      new CompanionObjectMemberResult(member, `object`) {

        override protected def buildItem(lookupItem: ScalaLookupItem,
                                         shouldImport: Boolean): Option[ScalaLookupItem] =
          if (shouldImport)
            super.buildItem(lookupItem, shouldImport)
          else
            None

        override protected def createInsertHandler: ScalaImportingInsertHandler = new ScalaImportingInsertHandler(`object`) {

          override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
            qualifyOnly(reference)
        }
      }
  }

  final class ExtensionLike(private val originalType: ScType,
                            override protected val place: ScConstructorOwner,
                            accessAll: Boolean)
                           (override protected val namePredicate: NamePredicate)
    extends CompanionObjectMembersFinder(place, accessAll)(namePredicate) {

    override protected type T = ScFunction

    override protected def findTargets(place: ScConstructorOwner): Iterable[PsiClass] =
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

    override protected def createResult(function: ScFunction,
                                        `object`: ScObject): CompanionObjectMemberResult =
      new CompanionObjectMemberResult(function, `object`) {

        override protected def createInsertHandler: InsertHandler[ScalaLookupItem] =
          (context: InsertionContext, _: ScalaLookupItem) => {
            val reference@ScReferenceExpression.withQualifier(qualifier) = context
              .getFile
              .findReferenceAt(context.getStartOffset)

            val functionName = member.name
            val ScMethodCall(methodReference: ScReferenceExpression, _) =
              replaceReference(reference, functionName + "(" + qualifier.getText + ")")

            if (member != methodReference.resolve) {
              val ScReferenceExpression.withQualifier(objectReference: ScReferenceExpression) =
                replaceReference(methodReference, `object`.name + "." + functionName)

              objectReference.bindToElement(`object`)
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