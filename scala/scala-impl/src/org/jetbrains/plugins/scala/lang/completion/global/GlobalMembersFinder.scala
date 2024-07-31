package org.jetbrains.plugins.scala.lang.completion.global

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.{PsiClass, PsiElement, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, JavaEnum, PsiElementExt, PsiNamedElementExt, ScalaEnumeration, SealedClassInheritors}
import org.jetbrains.plugins.scala.lang.completion
import org.jetbrains.plugins.scala.lang.completion.{global, regardlessAccessibility}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isStatic}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

sealed abstract class GlobalMembersFinder protected(protected val place: ScExpression,
                                                    protected val accessAll: Boolean) {

  protected final def isAccessible(member: PsiMember): Boolean =
    accessAll ||
      completion.isAccessible(member)(place)

  final def lookupItems: Iterable[LookupElement] = {
    val candidatesRes = candidates
    val candidatesApplicable = candidatesRes.filter(_.isApplicable)
    val lookupItems = candidatesApplicable.map(_.createLookupItem)
    lookupItems
  }

  protected[global] def candidates: Iterable[GlobalMemberResult]

  protected final def contextsOfType[E <: PsiElement : reflect.ClassTag]: Iterable[E] = place
    .contexts
    .filterByType[E]
    .iterator.to(Iterable)

  protected[global] final def findStableScalaFunctions(functions: Iterable[ScFunction])
                                                      (classesToImport: ScFunction => Set[ScObject])
                                                      (constructor: (ScFunction, ScObject) => GlobalMemberResult): Iterable[GlobalMemberResult] = for {
    function <- functions
    if !function.isSpecial &&
      isAccessible(function)

    classToImport <- classesToImport(function)
    if !isImplicit(classToImport) // filter out type class instances, such as scala.math.Numeric.String, to avoid too many results.
  } yield constructor(function, classToImport)

  protected[global] final def findStableScalaProperties(properties: Iterable[ScValueOrVariable])
                                                       (classesToImport: ScValueOrVariable => Set[ScObject])
                                                       (constructor: (ScTypedDefinition, ScObject) => GlobalMemberResult): Iterable[GlobalMemberResult] = for {
    property <- properties
    if isAccessible(property)

    classToImport <- classesToImport(property)
    elementToImport <- property.declaredElements
  } yield constructor(elementToImport, classToImport)

  protected[global] final def findStaticJavaMembers[M <: PsiMember](members: Iterable[M])
                                                                   (constructor: (M, PsiClass) => GlobalMemberResult): Iterable[GlobalMemberResult] = for {
    member <- members
    if isStatic(member) &&
      isAccessible(member)

    //noinspection ScalaWrongPlatformMethodsUsage
    classToImport = member.getContainingClass
    if classToImport != null &&
      isAccessible(classToImport)
  } yield constructor(member, classToImport)

  // todo import setting reconsider
  protected[global] final def objectCandidates[T <: ScTypedDefinition](typeDefinitions: Iterable[ScTypeDefinition])
                                                                      (namedElements: ScMember => Seq[T])
                                                                      (constructor: (T, ScObject) => GlobalMemberResult): Iterable[GlobalMemberResult] = for {
    ThisOrCompanionObject(targetObject) <- typeDefinitions

    member <- targetObject.members
    if isAccessible(member)

    namedElement <- namedElements(member)
  } yield constructor(namedElement, targetObject)
}

private[completion] abstract class ByTypeGlobalMembersFinder protected(protected val originalType: ScType,
                                                                       place: ScExpression,
                                                                       accessAll: Boolean)
  extends GlobalMembersFinder(place, accessAll)

private[completion] object ByTypeGlobalMembersFinder {

  def apply(originalType: ScType,
            place: ScExpression,
            invocationCount: Int): Seq[ByTypeGlobalMembersFinder] = {
    val accessAll = regardlessAccessibility(invocationCount)

    Seq(
      new ExtensionMethodsFinder(originalType, place, accessAll),
      new HoogleFinder(originalType, place, accessAll),
    )
  }
}

private[completion] abstract class ByPlaceGlobalMembersFinder protected(override protected val place: ScReferenceExpression,
                                                                        accessAll: Boolean)
  extends GlobalMembersFinder(place, accessAll) {

  protected object NameAvailability extends global.NameAvailability {

    import NameAvailabilityState._

    private lazy val elements = place
      .completionVariants()
      .map(_.element)
      .toSet

    override def apply(element: PsiNamedElement): NameAvailabilityState =
      if (elements.contains(element)) AVAILABLE
      else if (elements.exists(_.name == element.name)) CONFLICT
      else NO_CONFLICT
  }

  override protected[global] final def candidates: Iterable[GlobalMemberResult] =
    allCandidates.filterNot(isEnumCase) // enum cases are handled by ScalaSmartCompletionContributor (SCL-22855)

  protected[global] def allCandidates: Iterable[GlobalMemberResult]

  private def isEnumCase(member: GlobalMemberResult): Boolean = {
    val members: Seq[PsiMember] = member.containingClass.toSeq.flatMap {
      case e: ScEnum => e.cases
      case JavaEnum(fields) => fields
      case ScalaEnumeration(values) => values
      case SealedClassInheritors(inheritors) => inheritors
      case _ => Seq.empty
    }
    members.contains(member.element)
  }
}

private[completion] object ByPlaceGlobalMembersFinder {

  def apply(place: ScReferenceExpression,
            matcher: PrefixMatcher,
            invocationCount: Int): Seq[ByPlaceGlobalMembersFinder] = {
    val accessAll = regardlessAccessibility(invocationCount)

    val finder = if (accessAll && matcher.getPrefix.nonEmpty)
      new StaticMembersFinder(place, completion.accessAll(invocationCount))(matcher.prefixMatches)
    else
      new LocallyImportableMembersFinder(place, accessAll)

    Seq(finder)
  }
}