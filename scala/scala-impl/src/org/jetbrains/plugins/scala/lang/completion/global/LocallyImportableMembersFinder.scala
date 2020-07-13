package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private[completion] final class LocallyImportableMembersFinder(override protected val place: ScReferenceExpression,
                                                               accessAll: Boolean)
  extends GlobalMembersFinder(place, accessAll)
    with CompanionObjectMembersFinder[ScTypedDefinition]
    with ImportableMembersFinder {

  override protected def candidates: Iterable[GlobalMemberResult] =
    super.candidates ++
      localCandidates

  private def localCandidates = for {
    target <- findTargets
    if target.isInstanceOf[ScImportsHolder]
    statement <- target
      .asInstanceOf[ScImportsHolder]
      .getImportStatements

    expression <- statement.importExprs
    qualifier = expression.qualifier
    if qualifier != null

    result <- qualifier.resolve match {
      case ScalaObject(importedObject) =>
        val classesToImport = Function.const(Set(importedObject))(_: ScMember)

        findFunctions(importedObject.functions)(classesToImport) ++
          findProperties(importedObject.properties)(classesToImport)
      case importedClass: PsiClass =>
        findStaticMethods(importedClass.getAllMethods) ++
          findStaticFields(importedClass.getAllFields)
      case _ => Iterable.empty
    }
  } yield result

  override protected def findTargets: Iterable[PsiElement] =
    place.withContexts.toIterable

  override protected def namedElementsIn(member: ScMember): Seq[ScTypedDefinition] = member match {
    case value: ScValueOrVariable => value.declaredElements
    case function: ScFunction if !function.isConstructor => Seq(function)
    case _ => Seq.empty
  }

  override protected def createResult(resolveResult: ScalaResolveResult,
                                      classToImport: ScObject): GlobalMemberResult =
    CompanionMemberResult(resolveResult, classToImport)

  private final case class CompanionMemberResult(override val resolveResult: ScalaResolveResult,
                                                 override val classToImport: ScObject)
    extends ImportableMemberResult(resolveResult, classToImport, None) {

    override protected def buildItem(lookupItem: ScalaLookupItem,
                                     shouldImport: Boolean): Option[ScalaLookupItem] =
      if (shouldImport)
        super.buildItem(lookupItem, shouldImport)
      else
        None
  }

  override protected def createMethodResult(methodToImport: PsiMethod,
                                            classToImport: PsiClass): ImportableMemberResult =
    LocallyImportableMemberResult(methodToImport, classToImport)

  override protected def createFieldResult(elementToImport: PsiNamedElement,
                                           classToImport: PsiClass): ImportableMemberResult =
    LocallyImportableMemberResult(elementToImport, classToImport)

  private final case class LocallyImportableMemberResult(elementToImport: PsiNamedElement,
                                                         override val classToImport: PsiClass)
    extends ImportableMemberResult(elementToImport, classToImport) {

    override protected def buildItem(lookupItem: ScalaLookupItem,
                                     shouldImport: Boolean): Option[ScalaLookupItem] =
      if (shouldImport)
        super.buildItem(lookupItem, shouldImport = false)
      else
        None

    override protected def createInsertHandler: ScalaImportingInsertHandler with GlobalMemberInsertHandler =
      new ScalaImportingInsertHandler.WithBinding(elementToImport, classToImport)
        with GlobalMemberInsertHandler {

        override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
          throw new IllegalStateException("shouldImport has been set to false explicitly")

        override protected def qualifyOnly(reference: ScReferenceExpression): Unit = {
          triggerGlobalMemberCompletionFeature()
          super.qualifyAndImport(reference)
        }
      }
  }

  private object ScalaObject {

    def unapply(element: PsiElement): Option[ScObject] = element match {
      case `object`: ScObject => Some(`object`)
      case `package`: ScPackage => `package`.findPackageObject(`package`.resolveScope)
      case _ => None
    }
  }
}
