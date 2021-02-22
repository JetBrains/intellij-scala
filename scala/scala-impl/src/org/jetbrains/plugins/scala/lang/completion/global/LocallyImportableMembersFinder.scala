package org.jetbrains.plugins.scala
package lang
package completion
package global

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}

import scala.annotation.nowarn

private final class LocallyImportableMembersFinder(place: ScReferenceExpression,
                                                   accessAll: Boolean)
  extends ByPlaceGlobalMembersFinder(place, accessAll) {

  import LocallyImportableMembersFinder._

  override protected[global] def candidates: Iterable[GlobalMemberResult] =
    importableCandidates(LocallyImportableMemberResult) ++
      companionObjectCandidates

  private def importableCandidates(constructor: (PsiNamedElement, PsiClass) => GlobalMemberResult): Iterable[GlobalMemberResult] = for {
    importHolder <- contextsOfType[ScImportsHolder]
    statement <- importHolder.getImportStatements

    expression <- statement.importExprs
    qualifier <- expression.qualifier.toSeq

    result <- qualifier.resolve match {
      case ScalaObject(importedObject) =>
        val classesToImport = Function.const(Set(importedObject))(_: ScMember)

        findStableScalaFunctions(importedObject.functions)(classesToImport)(constructor) ++
          findStableScalaProperties(importedObject.properties)(classesToImport)(constructor)
      case importedClass: PsiClass =>
        findStaticJavaMembers(importedClass.getAllMethods)(constructor) ++
          findStaticJavaMembers(importedClass.getAllFields)(constructor)
      case _ => Iterable.empty
    }
  } yield result

  private def companionObjectCandidates = objectCandidates(contextsOfType[ScTypeDefinition]) {
    case value: ScValueOrVariable => value.declaredElements
    case function: ScFunction if !(function.isConstructor || function.isSpecial) => Seq(function)
    case _ => Seq.empty
  }(LocallyImportableMemberResult)

  @nowarn("msg=The outer reference in this type test cannot be checked at run time")
  private final case class LocallyImportableMemberResult(elementToImport: PsiNamedElement,
                                                         override val classToImport: PsiClass)
    extends GlobalMemberResult(elementToImport, classToImport)(NameAvailability) {

    import NameAvailabilityState._

    private[global] override def isApplicable: Boolean =
      super.isApplicable &&
        nameAvailabilityState != AVAILABLE

    override protected def buildItem(lookupItem: ScalaLookupItem): LookupElement = {
      lookupItem.shouldImport = nameAvailabilityState == CONFLICT
      super.buildItem(lookupItem)
    }

    override protected def createInsertHandler: InsertHandler[LookupElement] =
      nameAvailabilityState match {
        case NO_CONFLICT => createGlobalMemberInsertHandler(resolveResult.getElement, classToImport)
        case _ => super.createInsertHandler
      }
  }
}

private object LocallyImportableMembersFinder {

  object ScalaObject {

    def unapply(element: PsiElement): Option[ScObject] = element match {
      case `object`: ScObject => Some(`object`)
      case `package`: ScPackage => `package`.findPackageObject(`package`.resolveScope)
      case _ => None
    }
  }

}
