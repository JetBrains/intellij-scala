package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.{InsertHandler, InsertionContext}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] sealed abstract class CompanionObjectMembersFinder(private val place: ScalaPsiElement)
                                                                      (private val namePredicate: NamePredicate)
  extends GlobalMembersFinder {

  // todo import, class scope import setting reconsider

  override protected final def candidates: Iterable[GlobalMemberResult] = for {
    ClassOrTrait(CompanionModule(targetObject)) <- place.withContexts.toIterable

    member <- functions(targetObject) ++ members(targetObject)

    if namePredicate(member.name)
  } yield createResult(member, targetObject)

  protected def functions(`object`: ScObject): Seq[ScFunction] =
    `object`.functions

  protected def members(`object`: ScObject): Seq[ScTypedDefinition] =
    `object`.members.flatMap {
      case value: ScValueOrVariable /* todo if isAccessible */ => value.declaredElements
      case _ => Seq.empty
    }

  protected def createResult(member: ScTypedDefinition,
                             `object`: ScObject): CompanionObjectMemberResult

  protected sealed abstract class CompanionObjectMemberResult(private val member: ScTypedDefinition,
                                                              private val `object`: ScObject)
    extends GlobalMemberResult(
      new ScalaResolveResult(member),
      member,
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

  final class Regular private(place: ScReferenceExpression)
                             (namePredicate: NamePredicate)
    extends CompanionObjectMembersFinder(place)(namePredicate) {

    override protected def createResult(member: ScTypedDefinition,
                                        `object`: ScObject): CompanionObjectMemberResult =
      new CompanionObjectMemberResult(member, `object`) {}
  }

  object Regular {

    def apply(place: ScReferenceExpression): NamePredicate => Regular =
      new Regular(place)(_)
  }

  final class ExtensionLike private(private val originalType: ScType,
                                    classOrTrait: ScConstructorOwner)
                                   (namePredicate: NamePredicate)
    extends CompanionObjectMembersFinder(classOrTrait)(namePredicate) {

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
      new CompanionObjectMemberResult(member, `object`) {

        override protected def patchItem(lookupItem: ScalaLookupItem): Unit = {
          lookupItem.setInsertHandler(createPostfixInsertHandler())
        }
      }

    private def createPostfixInsertHandler() = new InsertHandler[ScalaLookupItem] {

      override def handleInsert(context: InsertionContext, item: ScalaLookupItem): Unit = {
        val reference@ScReferenceExpression.withQualifier(qualifier) = context
          .getFile
          .findReferenceAt(context.getStartOffset)

        val Some(targetObject: ScObject) = item.classToImport
        val Some(targetFunction: ScFunction) = item.elementToImport

        val newReference = createExpressionWithContextFromText(
          targetObject.name + "." + targetFunction.name + "(" + qualifier.getText + ")",
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

  object ExtensionLike {

    def apply(originalType: ScType,
              classOrTrait: ScConstructorOwner): NamePredicate => ExtensionLike =
      new ExtensionLike(originalType, classOrTrait)(_)
  }
}