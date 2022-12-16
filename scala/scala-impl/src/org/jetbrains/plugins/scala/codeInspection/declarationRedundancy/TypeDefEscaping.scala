package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}


/**
 * Utility to scrape all types that escape through public members of a given type definition. The current use
 * case is to assist in [[ScalaAccessCanBeTightenedInspection]], but maybe it could also be used to improve
 * our error highlighting. See SCL-20855.
 *
 * [[getEscapeInfos]] is the main and only entrypoint.
 */
private[declarationRedundancy] object TypeDefEscaping {

  /**
   * If any of the scraped [[ScType]] instances are parameterized, this method will destructure those into a list of
   * non-parameterized [[ScType]] instances. Note that there are at least 2 seemingly different forms I'm currently
   * aware of, that are both handled here, and that are equally crucial in detecting escaping type definitions.
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
   * Any types that are not parameterized are returned in their original form.
   */
  private def destructureParameterizedTypes(types: Seq[ScType]): Seq[ScType] = types.flatMap {
    case parameterizedType: ScParameterizedType =>
      parameterizedType.designator +: destructureParameterizedTypes(parameterizedType.typeArguments)
    case t => Seq(t)
  }

  /**
   * Since we're only interested in whether a type escapes from its defining
   * scope, we can safely filter out any scraped types that are defined in
   * another file, including Scala Standard Library definitions.
   */
  private def isScTypeDefinedInFile(scType: ScType, file: PsiFile): Boolean =
    scType.extractClass.forall(_.getContainingFile == file)

  private def isPrivate(member: ScMember): Boolean =
    member.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)

  private def getTypeParameterTypes(owner: ScTypeParametersOwner): Seq[ScType] =
    owner.typeParameters.flatMap { typeParam =>
      typeParam.viewTypeElement ++
        typeParam.upperTypeElement ++
        typeParam.lowerTypeElement ++
        typeParam.contextBoundTypeElement
    }.flatMap(_.`type`().toSeq)

  final class EscapeInfo(val member: ScMember, val types: Seq[ScType])

  private final object EscapeInfo {
    def apply(member: ScMember, types: Seq[ScType]) = new EscapeInfo(member, types)
  }

  /**
   * Recursively scrape all types that escape through public members of `typeDef`.
   *
   * The result is a list of [[EscapeInfo]]s, associating a public member with a list of the types that escape through it.
   *
   * Keep in mind that it's the client code's responsibility to exclude irrelevant members when consuming the results.
   * For example:
   * {{{
   * object Foo {
   *   class Bar
   *   def f(): Bar = new Bar
   * }
   * }}}
   * Let's say you want to figure out if `Bar` can be private. In that case you want to get the types that
   * escape through members of `Bar`'s direct parent, which is `Foo`. Naturally the `Foo`'s collection of members
   * includes `Bar`, so when you iterate through `getEscapeInfos`'s results, you have to skip `Bar`.
   *
   * The reason `getEscapeInfos` caches and returns all public members of `Foo` and not just `Bar`'s siblings, is so
   * we can reuse cached results when we're running the same inspection against a sibling type definition of `Bar`.
   *
   * Another caveat is that when you ask for escaping types of members of `Foo`, you will also want to do that for
   * `Foo`'s companion (if it has one). Again, this is the responsibility of client code, and again this design is
   * the result of the fact that we want to cache any reusable results.
   *
   * See [[ScalaAccessCanBeTightenedInspection]] for example usage.
   */
  def getEscapeInfos(typeDef: ScTypeDefinition): Seq[EscapeInfo] = cachedInUserData("TypeDefEscaping.getEscapeInfosOfTypeDefMembers", typeDef, ModTracker.anyScalaPsiChange, Tuple1(typeDef)) {

    val typeDefFile = typeDef.getContainingFile

    def destructureAndFilter(types: Seq[ScType]): Seq[ScType] =
      destructureParameterizedTypes(types)
        .filter(t => isScTypeDefinedInFile(t, typeDefFile))
        .filterNot(_.is[TypeParameterType])

    typeDef.members.collect { member =>

      ProgressManager.checkCanceled()

      member match {
        case typeDef: ScTypeDefinition if !isPrivate(typeDef) =>

          val types = typeDef.`type`().toSeq ++ getTypeParameterTypes(typeDef)
          val childTypes = (typeDef +: typeDef.baseCompanion.toSeq).flatMap(getEscapeInfos)

          EscapeInfo(typeDef, destructureAndFilter(types)) +: childTypes

        case typeAlias: ScTypeAliasDefinition if !isPrivate(typeAlias) =>
          val types = getTypeParameterTypes(typeAlias) ++ typeAlias.aliasedType.toSeq
          Seq(EscapeInfo(typeAlias, destructureAndFilter(types)))

        case primaryConstructor: ScPrimaryConstructor =>

          def isPrivateOrNonMember(p: ScClassParameter): Boolean =
            isPrivate(p) || !p.isClassMember

          val parametersThroughWhichTypeDefsCanEscape = if (isPrivate(primaryConstructor)) {
            primaryConstructor.parameters.filterNot(isPrivateOrNonMember)
          } else {
            primaryConstructor.parameters.filterNot(p => isPrivateOrNonMember(p) && p.getDefaultExpression.isEmpty)
          }

          val types = parametersThroughWhichTypeDefsCanEscape.flatMap(p => p.`type`().toSeq)

          Seq(EscapeInfo(primaryConstructor, destructureAndFilter(types)))

        case function: ScFunction if !isPrivate(function) =>

          val returnAndParameterTypes = function.`type`().toSeq
          val types = returnAndParameterTypes ++ getTypeParameterTypes(function)

          Seq(EscapeInfo(function, destructureAndFilter(types)))

        case typeable: Typeable if !isPrivate(typeable) =>
          val types = typeable.`type`().toSeq
          Seq(EscapeInfo(typeable, destructureAndFilter(types)))

        case _ => Seq.empty
      }
    }.flatten
  }
}
