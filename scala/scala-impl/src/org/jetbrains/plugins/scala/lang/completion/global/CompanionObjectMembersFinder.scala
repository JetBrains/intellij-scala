package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] sealed abstract class CompanionObjectMembersFinder[E <: PsiElement](override protected val place: E,
                                                                                        accessAll: Boolean)
                                                                                       (override protected val namePredicate: NamePredicate)
  extends GlobalMembersFinderBase(place, accessAll)(namePredicate) {

  // todo import, class scope import setting reconsider

  override protected final def candidates: Iterable[GlobalMemberResult] = for {
    ClassOrTrait(CompanionModule(targetObject)) <- findTargets(place)

    member <- functions(targetObject) ++ members(targetObject)

    if namePredicate(member.name)
  } yield createResult(member, targetObject)

  protected def findTargets(place: E): Iterable[PsiElement]

  protected def functions(`object`: ScObject): Seq[ScFunction] =
    `object`
      .functions
      .filter(isAccessible)

  protected def members(`object`: ScObject): Seq[ScTypedDefinition] =
    `object`.members.flatMap {
      case value: ScValueOrVariable if isAccessible(value) => value.declaredElements
      case _ => Seq.empty
    }

  protected def createResult(member: ScTypedDefinition,
                             `object`: ScObject): CompanionObjectMemberResult

  protected sealed abstract class CompanionObjectMemberResult(protected val member: ScTypedDefinition,
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

    override protected def findTargets(place: ScReferenceExpression): Iterable[PsiElement] =
      place.withContexts.toIterable

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

    override protected def findTargets(place: ScConstructorOwner): Iterable[PsiClass] =
      place +: place.supers

    override protected def functions(`object`: ScObject): Seq[ScFunction] = for {
      function <- super.functions(`object`)

      parameters = function.parameters
      if parameters.size == 1

      parameterType <- parameters.head
        .getRealParameterType
        .toOption
      if originalType.conforms(parameterType)
    } yield function

    override protected def members(`object`: ScObject) =
      Seq.empty[ScTypedDefinition]

    override protected def createResult(member: ScTypedDefinition,
                                        `object`: ScObject): CompanionObjectMemberResult =
      new CompanionObjectMemberResult(member.asInstanceOf[ScFunction], `object`) {

        override protected def createInsertHandler: InsertHandler[ScalaLookupItem] =
          (context: InsertionContext, _: ScalaLookupItem) => {
            val reference@ScReferenceExpression.withQualifier(qualifier) = context
              .getFile
              .findReferenceAt(context.getStartOffset)

            val newReference = createExpressionWithContextFromText(
              `object`.name + "." + member.name + "(" + qualifier.getText + ")",
              reference.getContext,
              reference
            )

            reference.replaceExpression(
              newReference,
              removeParenthesis = true
            )
          }
      }
  }
}