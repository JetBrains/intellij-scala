package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.psi.{PsiClass, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMemberExt, PsiMethodExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.completion.StaticMembersFinder.classesToImportFor
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isStatic}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}

private final class StaticMembersFinder(place: ScReferenceExpression, matcher: PrefixMatcher, accessAll: Boolean)
  extends GlobalMembersFinder {

  private implicit val ElementScope(project, scope) = place.elementScope
  private val cacheManager = ScalaShortNamesCacheManager.getInstance
  private def nameMatches(s: String): Boolean = matcher.prefixMatches(s)

  override protected def candidates: Iterable[GlobalMemberResult] = methodsLookups ++ fieldsLookups ++ propertiesLookups

  private def methodsLookups = for {
    method <- cacheManager.allFunctions(nameMatches) ++ cacheManager.allMethods(nameMatches)
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
    field <- cacheManager.allFields(nameMatches)
    if isAccessible(field) && isStatic(field)

    classToImport = field.containingClass
    if classToImport != null && isAccessible(classToImport)
  } yield StaticMemberResult(field, classToImport)

  private def propertiesLookups = for {
    property <- cacheManager.allProperties(nameMatches)
    if isAccessible(property)

    namedElement = property.declaredElements.head

    classToImport <- classesToImportFor(property)
  } yield StaticMemberResult(namedElement, classToImport)

  private def isAccessible(member: PsiMember) = accessAll ||
    ResolveUtils.isAccessible(member, place, forCompletion = true)

  private final case class StaticMemberResult(elementToImport: PsiNamedElement,
                                              containingClass: PsiClass,
                                              isOverloadedForClassName: Boolean = false) extends GlobalMemberResult {
    val classToImport: PsiClass = containingClass

    override protected val resolveResult = new ScalaResolveResult(elementToImport)
  }

}

private object StaticMembersFinder {

  def apply(place: ScReferenceExpression, prefixMatcher: PrefixMatcher, accessAll: Boolean): Option[StaticMembersFinder] =
    if (prefixMatcher.getPrefix.nonEmpty) Some(new StaticMembersFinder(place, prefixMatcher, accessAll))
    else None

  private def classesToImportFor(m: PsiMember): Iterable[PsiClass] = {
    val cClass = m.containingClass
    if (cClass == null)
      return Seq.empty

    if (isStatic(m)) Seq(cClass)
    else {
      cClass.asOptionOf[ScTemplateDefinition]
        .map(ScalaInheritors.findInheritorObjects).getOrElse(Set.empty)
    }
  }

}