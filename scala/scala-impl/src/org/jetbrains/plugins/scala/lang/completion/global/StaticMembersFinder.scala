package org.jetbrains.plugins.scala
package lang
package completion
package global

import java.util.Arrays.asList

import com.intellij.codeInsight.completion.JavaCompletionUtil.putAllMethods
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
  extends GlobalMembersFinder(place, accessAll)
    with ImportableMembersFinder {

  override protected def candidates: Iterable[GlobalMemberResult] = {
    implicit val scope: GlobalSearchScope = place.resolveScope
    val cacheManager = ScalaShortNamesCacheManager.getInstance(place.getProject)

    findFunctions(cacheManager.allFunctions(namePredicate))(findInheritorObjectsForOwner) ++
      findProperties(cacheManager.allProperties(namePredicate))(findInheritorObjectsForOwner) ++
      findStaticMethods(cacheManager.allMethods(namePredicate)) ++
      findStaticFields(cacheManager.allFields(namePredicate))
  }

  //noinspection ScalaWrongPlatformMethodsUsage
  override protected def createMethodResult(methodToImport: PsiMethod,
                                            classToImport: PsiClass): ImportableMemberResult = {
    val overloadsToImport = methodToImport match {
      case methodToImport: ScFunction =>
        val `object` = classToImport.asInstanceOf[ScObject]
        val name = methodToImport.name

        `object`.allFunctionsByName(name).toArray match {
          case Array() if methodToImport.isParameterless =>
            // todo to be investigated
            return StaticFieldResult(`object`.allTermsByName(name).head, classToImport)
          case functions => functions
        }
      case _ => classToImport.findMethodsByName(methodToImport.getName, true)
    }

    StaticMethodResult(overloadsToImport, classToImport)
  }

  private final case class StaticMethodResult(overloadsToImport: Array[PsiMethod],
                                              override val classToImport: PsiClass)
    extends ImportableMemberResult(
      overloadsToImport match {
        case Array() => throw new IllegalArgumentException(s"$classToImport doesn't contain corresponding members")
        case Array(first) => first
        case Array(first, second, _*) => if (first.isParameterless) second else first
      },
      classToImport
    ) {

    override protected def buildItem(lookupItem: ScalaLookupItem,
                                     shouldImport: Boolean): Option[ScalaLookupItem] = {
      putAllMethods(lookupItem, asList(overloadsToImport: _*))
      super.buildItem(lookupItem, shouldImport)
    }
  }

  override protected def createFieldResult(elementToImport: PsiNamedElement,
                                           classToImport: PsiClass): ImportableMemberResult =
    StaticFieldResult(elementToImport, classToImport)

  private final case class StaticFieldResult(elementToImport: PsiNamedElement,
                                             override val classToImport: PsiClass)
    extends ImportableMemberResult(elementToImport, classToImport)
}
