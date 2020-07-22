package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.lookup.LookupElement
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
    localCandidates ++
      super.candidates

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

  override protected def namedElementsIn(member: ScMember): Seq[ScTypedDefinition] = member match {
    case value: ScValueOrVariable => value.declaredElements
    case function: ScFunction if !(function.isConstructor || function.isSpecial) => Seq(function)
    case _ => Seq.empty
  }

  override protected def createResult(resolveResult: ScalaResolveResult,
                                      classToImport: ScObject): GlobalMemberResult =
    LocallyImportableMemberResult(resolveResult, classToImport, None)

  override protected def createMethodResult(methodToImport: PsiMethod,
                                            classToImport: PsiClass): ImportableMemberResult =
    new LocallyImportableMemberResult(methodToImport, classToImport)

  override protected def createFieldResult(elementToImport: PsiNamedElement,
                                           classToImport: PsiClass): ImportableMemberResult =
    new LocallyImportableMemberResult(elementToImport, classToImport)

  private final case class LocallyImportableMemberResult(override protected val resolveResult: ScalaResolveResult,
                                                         override protected val classToImport: PsiClass,
                                                         containingClass: Option[PsiClass])
    extends ImportableMemberResult(resolveResult, classToImport, containingClass) {

    import NameAvailabilityState._

    def this(elementToImport: PsiNamedElement,
             classToImport: PsiClass) = this(
      new ScalaResolveResult(elementToImport),
      classToImport,
      Some(classToImport)
    )

    override protected def buildItem(lookupItem: ScalaLookupItem,
                                     state: NameAvailabilityState): LookupElement = state match {
      case AVAILABLE => null
      case CONFLICT => super.buildItem(lookupItem, CONFLICT)
      case NO_CONFLICT => super.buildItem(lookupItem, AVAILABLE)
    }

    override protected def createInsertHandler(state: NameAvailabilityState): ScalaImportingInsertHandler with GlobalMemberInsertHandler =
      state match {
        case AVAILABLE =>
          new ScalaImportingInsertHandler.WithBinding(
            resolveResult.getElement,
            classToImport
          ) with GlobalMemberInsertHandler {

            override protected def qualifyAndImport(reference: ScReferenceExpression): Unit =
              throw new IllegalStateException("shouldImport has been set to false explicitly")

            override protected def qualifyOnly(reference: ScReferenceExpression): Unit = {
              triggerGlobalMemberCompletionFeature()
              super.qualifyAndImport(reference)
            }
          }
        case _ => super.createInsertHandler(state)
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
