package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.findInheritorObjects
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isStatic}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] final class StaticMembersFinder(override protected val place: ScReferenceExpression,
                                                    accessAll: Boolean)
                                                   (override protected val namePredicate: NamePredicate)
  extends GlobalMembersFinderBase(place, accessAll)(namePredicate) {

  import StaticMembersFinder._

  private val cacheManager = ScalaShortNamesCacheManager.getInstance(place.getProject)

  override protected def candidates: Iterable[GlobalMemberResult] = {
    implicit val scope: GlobalSearchScope = place.resolveScope

    methodsLookups ++
      fieldsLookups ++
      propertiesLookups
  }

  private def methodsLookups(implicit scope: GlobalSearchScope) = for {
    method <- cacheManager.allFunctions(namePredicate) ++ cacheManager.allMethods(namePredicate)
    if isAccessible(method)

    classToImport <- classesToImportFor(method)

    // filter out type class instances, such as scala.math.Numeric.String, to avoid too many results.
    if !isImplicit(classToImport)

    methodName = method.name
    overloads = classToImport match {
      case o: ScObject => o.allFunctionsByName(methodName).toSeq
      case _ => classToImport.getAllMethods.filter(_.name == methodName).toSeq
    }

    first <- overloads.headOption
    (namedElement, isOverloadedForClassName) = overloads.lift(1).fold((first, false)) { second =>
      (if (first.isParameterless) second else first, true)
    }
  } yield StaticMemberResult(namedElement, classToImport, isOverloadedForClassName)

  private def fieldsLookups(implicit scope: GlobalSearchScope) = for {
    field <- cacheManager.allFields(namePredicate)
    if isAccessible(field) && isStatic(field)

    classToImport = field.containingClass
    if classToImport != null && isAccessible(classToImport)
  } yield StaticMemberResult(field, classToImport)

  private def propertiesLookups(implicit scope: GlobalSearchScope) = for {
    property <- cacheManager.allProperties(namePredicate)
    if isAccessible(property)

    namedElement <- property.declaredElements

    classToImport <- classesToImportFor(property)
  } yield StaticMemberResult(namedElement, classToImport)

  private final case class StaticMemberResult(elementToImport: PsiNamedElement,
                                              classToImport: PsiClass,
                                              isOverloadedForClassName: Boolean = false)
    extends GlobalMemberResult(
      new ScalaResolveResult(elementToImport),
      classToImport,
      Some(classToImport)
    ) {
    override protected def buildItem(lookupItem: ScalaLookupItem,
                                     shouldImport: Boolean): Option[ScalaLookupItem] = {
      lookupItem.isOverloadedForClassName = isOverloadedForClassName
      super.buildItem(lookupItem, shouldImport)
    }

    override protected def createInsertHandler: ScalaImportingInsertHandler =
      new ScalaImportingInsertHandler(classToImport)
        with GlobalMemberInsertHandler {

        override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = {
          triggerGlobalMemberCompletionFeature()
          qualifyOnly(reference)
        }
      }
  }

}

object StaticMembersFinder {

  private def classesToImportFor(member: PsiMember): Set[_ <: PsiClass] = member.containingClass match {
    case clazz: ScTemplateDefinition => findInheritorObjects(clazz)
    case clazz: PsiClass if isStatic(member) => Set(clazz)
    case _ => Set.empty
  }
}