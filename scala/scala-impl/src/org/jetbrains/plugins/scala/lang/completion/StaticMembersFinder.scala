package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.StaticMembersFinder.classesToImportFor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isStatic}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors.findInheritorObjects
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private final class StaticMembersFinder(namePredicate: String => Boolean)
                                       (isAccessible: PsiMember => Boolean)
                                       (implicit private val project: Project,
                                        private val scope: GlobalSearchScope)
  extends GlobalMembersFinder {

  private val cacheManager = ScalaShortNamesCacheManager.getInstance

  override protected def candidates: Iterable[GlobalMemberResult] = methodsLookups ++ fieldsLookups ++ propertiesLookups

  private def methodsLookups = for {
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

  private def fieldsLookups = for {
    field <- cacheManager.allFields(namePredicate)
    if isAccessible(field) && isStatic(field)

    classToImport = field.containingClass
    if classToImport != null && isAccessible(classToImport)
  } yield StaticMemberResult(field, classToImport)

  private def propertiesLookups = for {
    property <- cacheManager.allProperties(namePredicate)
    if isAccessible(property)

    namedElement = property.declaredElements.head

    classToImport <- classesToImportFor(property)
  } yield StaticMemberResult(namedElement, classToImport)

  private final case class StaticMemberResult(override val elementToImport: PsiNamedElement,
                                              override val containingClass: PsiClass,
                                              override val isOverloadedForClassName: Boolean = false) extends GlobalMemberResult {
    override val classToImport: PsiClass = containingClass

    override protected val resolveResult = new ScalaResolveResult(elementToImport)
  }

}

private object StaticMembersFinder {

  def apply(place: ScReferenceExpression,
            matcher: PrefixMatcher,
            invocationCount: Int): Option[StaticMembersFinder] =
    if (matcher.getPrefix.isEmpty) {
      None
    } else {
      val isAccessible = invocationCount >= 3 ||
        completion.isAccessible(_: PsiMember)(place)
      Some(new StaticMembersFinder(matcher.prefixMatches)(isAccessible)(place.getProject, place.resolveScope))
    }

  private def classesToImportFor(member: PsiMember): Set[_ <: PsiClass] = member.containingClass match {
    case clazz: ScTemplateDefinition => findInheritorObjects(clazz)
    case clazz: PsiClass if isStatic(member) => Set(clazz)
    case _ => Set.empty
  }
}