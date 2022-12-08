package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

private[declarationRedundancy] object TypeDefEscaping {

  /**
   * A member and a type that escapes through it. For example:
   *
   * {{{def foo[T <: A]: Seq[Bar]}}}
   *
   * This member will have 3 [[EscapeInfo]]s associated with it:
   * {{{(foo, A), (foo, Seq) and (foo, Bar)}}}
   */
  sealed case class EscapeInfo(member: ScMember, escapingType: ScType)

  /**
   * If an `EscapeInfo`'s `escapingType` is parameterized, this method will destructure it into a list of
   * `EscapeInfo`s with non-parameterized types. Note that there are at least 2 seemingly different forms
   * I'm currently aware of that are both handled here, and that both are crucial in detecting escaping
   * type definitions.
   *
   * Form 1: A => B.
   * Let's take the below example:
   * {{{ object A { class B; def foo(b: B) = () } }}}
   *
   * `foo` is an [[ScFunction]] which inherits from [[Typeable]]. When we call {{{`type`()}}} against this
   * [[Typeable]], the result is an [[ScParameterizedType]] of the form `A.B => Unit`, which, upon destructuring,
   * becomes the following list of [[ScType]]s: `A.B, Function1, Unit`.
   *
   * Form 2: `A[B]`.
   * Take the below example:
   * {{{ object A { class B; def foo(): Option[Seq[B]] = ??? } }}}
   *
   * As we have seen in Form 1, {{{`type`()}}} includes an [[ScFunction]]s return type. It also includes
   * its type parameter types, and its parameter types, but for the sake of example let's focus on the
   * return type.
   *
   * If the return type is a parameterized type of the form `A[B]`, this type is also destructured
   * into a list of its non-parameterized constituents. So `Option[Seq[B]]` becomes `Option, Seq, B`.
   *
   * The resulting [[EscapeInfo]]s will all have the same member associated with them as this recursive
   * method's root input does.
   *
   * If `info.escapingType` is not a parameterized type, the same info is returned, wrapped in a `Seq`.
   */
  private def destructureParameterizedTypes(info: EscapeInfo): Seq[EscapeInfo] = info match {
    case EscapeInfo(member: ScMember, parameterizedType: ScParameterizedType) =>

      val outerTypeEscapeInfo = Seq(EscapeInfo(member, parameterizedType.designator))

      outerTypeEscapeInfo ++ parameterizedType.typeArguments.flatMap(t => destructureParameterizedTypes(EscapeInfo(member, t)))

    case escapeInfo => Seq(escapeInfo)
  }

  /**
   * Since we're only interested in whether a type escapes from its definining
   * scope, we can safely filter out any scraped types that are defined in
   * another file, including Scala Standard Library definitions.
   */
  private def isScTypeDefinedInFile(scType: ScType, file: PsiFile): Boolean =
    scType.extractClass.forall(_.getContainingFile == file)

  private def isPrivate(member: ScMember): Boolean =
    member.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)

  /**
   * Get the [[EscapeInfo]]s of a given `typeDef`'s members. See [[EscapeInfo]].
   */
  @CachedInUserData(typeDef, ModTracker.anyScalaPsiChange)
  def getEscapeInfosOfTypeDefMembers(typeDef: ScTypeDefinition): Seq[EscapeInfo] = {

    val typeDefFile = typeDef.getContainingFile

    typeDef.members.flatMap { member =>

      ProgressManager.checkCanceled()

      val escapeInfos = member match {

        case typeDef: ScTypeDefinition if !isPrivate(typeDef) =>
          val typeDefEscapeInfo = typeDef.`type`().toSeq.map(EscapeInfo(typeDef, _))

          val typeDefAndCompanion = typeDef +: typeDef.baseCompanion.toSeq
          val typeDefAndCompanionMembersEscapeInfos = typeDefAndCompanion.flatMap(getEscapeInfosOfTypeDefMembers)

          typeDefAndCompanionMembersEscapeInfos ++ typeDefEscapeInfo

        case typeAlias: ScTypeAliasDefinition if !isPrivate(typeAlias) =>
          typeAlias.aliasedType.toSeq.map(EscapeInfo(typeAlias, _))

        case primaryConstructor: ScPrimaryConstructor =>

          def isPrivateOrNonMember(p: ScClassParameter): Boolean =
            isPrivate(p) || !p.isClassMember

          val parametersThroughWhichTypeDefsCanEscape = if (isPrivate(primaryConstructor)) {
            primaryConstructor.parameters.filterNot(isPrivateOrNonMember)
          } else {
            primaryConstructor.parameters.filterNot(p => isPrivateOrNonMember(p) && p.getDefaultExpression.isEmpty)
          }

          parametersThroughWhichTypeDefsCanEscape.flatMap(p => p.`type`().toSeq.map(EscapeInfo(p, _)))

        case function: ScFunction if !isPrivate(function) =>
          val returnAndParameterTypes: Seq[ScType] = function.`type`().toSeq

          val typeParameterTypes: Seq[ScType] =
            function.typeParameters.flatMap { typeParam =>
              typeParam.viewTypeElement ++
                typeParam.upperTypeElement ++
                typeParam.lowerTypeElement ++
                typeParam.contextBoundTypeElement
            }.flatMap(_.`type`().toSeq)

          (returnAndParameterTypes ++ typeParameterTypes).map(EscapeInfo(function, _))

        case typeable: Typeable if !isPrivate(typeable) =>
          typeable.`type`().toSeq.map(EscapeInfo(typeable, _))

        case _ =>
          Seq.empty
      }

      escapeInfos.flatMap { escapeInfo =>
        val destructuredTypes = destructureParameterizedTypes(escapeInfo)
        destructuredTypes
      }.filter { escapeInfo =>
        val typeIsDefinedInSameFileAsTypeDef = isScTypeDefinedInFile(escapeInfo.escapingType, typeDefFile)
        typeIsDefinedInSameFileAsTypeDef
      }
    }
  }
}
