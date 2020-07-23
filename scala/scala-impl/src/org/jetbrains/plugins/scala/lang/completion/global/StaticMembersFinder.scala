package org.jetbrains.plugins.scala
package lang
package completion
package global

import java.util.Arrays.asList

import com.intellij.codeInsight.completion.JavaCompletionUtil.putAllMethods
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.findInheritorObjectsForOwner
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

private[completion] final class StaticMembersFinder(override protected val place: ScReferenceExpression,
                                                    accessAll: Boolean)
                                                   (private val namePredicate: String => Boolean)
  extends GlobalMembersFinder(place, accessAll) {

  import StaticMembersFinder._

  override protected[global] def candidates: Iterable[GlobalMemberResult] = {
    implicit val scope: GlobalSearchScope = place.resolveScope
    val cacheManager = ScalaShortNamesCacheManager.getInstance(place.getProject)

    findStableScalaFunctions(cacheManager.allFunctions(namePredicate))(findInheritorObjectsForOwner) {
      StaticMethodResult(_, _)
    } ++ findStableScalaProperties(cacheManager.allProperties(namePredicate))(findInheritorObjectsForOwner) {
      StaticFieldResult(_, _)
    } ++ findStaticJavaMembers(cacheManager.allMethods(namePredicate)) {
      StaticMethodResult(_, _)
    } ++ findStaticJavaMembers(cacheManager.allFields(namePredicate)) {
      StaticFieldResult(_, _)
    }
  }
}

private object StaticMembersFinder {

  object StaticMethodResult {

    def apply(methodToImport: ScFunction,
              classToImport: ScObject): GlobalMemberResult = {
      val name = methodToImport.name

      classToImport.allFunctionsByName(name).toArray match {
        case Array() if methodToImport.isParameterless =>
          // todo to be investigated
          StaticFieldResult(
            classToImport.allTermsByName(name).head,
            classToImport
          )
        case overloadsToImport => StaticMethodResult(overloadsToImport, classToImport)
      }
    }

    def apply(methodToImport: PsiMethod,
              classToImport: PsiClass): StaticMethodResult =
      StaticMethodResult(
        //noinspection ScalaWrongPlatformMethodsUsage
        classToImport.findMethodsByName(methodToImport.getName, true),
        classToImport
      )
  }

  final case class StaticMethodResult(overloadsToImport: Array[PsiMethod],
                                      override val classToImport: PsiClass)
    extends GlobalMemberResult(
      overloadsToImport match {
        case Array() => throw new IllegalArgumentException(s"$classToImport doesn't contain corresponding members")
        case Array(first) => first
        case Array(first, second, _*) => if (first.isParameterless) second else first
      },
      classToImport
    ) {

    override protected def buildItem(lookupItem: ScalaLookupItem,
                                     state: NameAvailabilityState): LookupElement = {
      putAllMethods(lookupItem, asList(overloadsToImport: _*))
      super.buildItem(lookupItem, state)
    }
  }

  final case class StaticFieldResult(elementToImport: PsiNamedElement,
                                     override val classToImport: PsiClass)
    extends GlobalMemberResult(elementToImport, classToImport)

}
