package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isImplicit, isStatic}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait ImportableMembersFinder {

  this: GlobalMembersFinder =>

  protected final def findFunctions(functions: Iterable[ScFunction])
                                   (classesToImport: ScFunction => Set[ScObject]): Iterable[ImportableMemberResult] = for {
    function <- functions
    if isAccessible(function)

    classToImport <- classesToImport(function)
    if !isImplicit(classToImport) // filter out type class instances, such as scala.math.Numeric.String, to avoid too many results.
  } yield createMethodResult(function, classToImport)

  protected final def findProperties(properties: Iterable[ScValueOrVariable])
                                    (classesToImport: ScValueOrVariable => Set[ScObject]): Iterable[ImportableMemberResult] = for {
    property <- properties
    if isAccessible(property)

    classToImport <- classesToImport(property)
    elementToImport <- property.declaredElements
  } yield createFieldResult(elementToImport, classToImport)

  protected final def findStaticMethods(methods: Iterable[PsiMethod]): Iterable[ImportableMemberResult] = for {
    method@StaticallyImportable(classToImport) <- methods
  } yield createMethodResult(method, classToImport)

  protected final def findStaticFields(fields: Iterable[PsiField]): Iterable[ImportableMemberResult] = for {
    field@StaticallyImportable(classToImport) <- fields
  } yield createFieldResult(field, classToImport)

  protected def createMethodResult(methodToImport: PsiMethod,
                                   classToImport: PsiClass): ImportableMemberResult

  protected def createFieldResult(elementToImport: PsiNamedElement,
                                  classToImport: PsiClass): ImportableMemberResult

  protected abstract class ImportableMemberResult protected(override protected val resolveResult: ScalaResolveResult,
                                                            override protected val classToImport: PsiClass,
                                                            containingClass: Option[PsiClass])
    extends GlobalMemberResult(resolveResult, classToImport, containingClass) {

    protected def this(elementToImport: PsiNamedElement,
                       classToImport: PsiClass) = this(
      new ScalaResolveResult(elementToImport),
      classToImport,
      Some(classToImport)
    )

    override protected def createInsertHandler: ScalaImportingInsertHandler with GlobalMemberInsertHandler =
      new ScalaImportingInsertHandler(classToImport)
        with GlobalMemberInsertHandler {

        override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = {
          triggerGlobalMemberCompletionFeature()
          qualifyOnly(reference)
        }
      }
  }

  private object StaticallyImportable {

    //noinspection ScalaWrongPlatformMethodsUsage
    def unapply(member: PsiMember): Option[PsiClass] = member.getContainingClass match {
      case null => None
      case classToImport if isStatic(member) &&
        isAccessible(member) &&
        isAccessible(classToImport) => Some(classToImport)
      case _ => None
    }
  }
}
