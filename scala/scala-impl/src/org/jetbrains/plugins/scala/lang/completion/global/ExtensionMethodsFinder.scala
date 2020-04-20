package org.jetbrains.plugins.scala
package lang
package completion
package global

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.implicits._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

private[completion] final class ExtensionMethodsFinder private(originalType: ScType, place: ScExpression)
  extends GlobalMembersFinder {

  private lazy val originalTypeMemberNames: collection.Set[String] = candidatesForType(originalType).map(_.name)

  override protected def candidates: Iterable[GlobalMemberResult] = for {
    (GlobalImplicitConversion(classToImport, elementToImport), conversionData) <- ImplicitConversionCache.getOrScheduleUpdate(place.resolveScope)(place.getProject)
    if ImplicitConversionProcessor.applicable(elementToImport, place)

    (resultType, _) <- ScImplicitlyConvertible.targetTypeAndSubstitutor(
      conversionData,
      originalType,
      place
    ).toIterable

    resolveResult <- candidatesForType(resultType)
    if !originalTypeMemberNames.contains(resolveResult.name)
  } yield ExtensionMethodCandidate(resolveResult, elementToImport, classToImport)

  private def candidatesForType(`type`: ScType): collection.Set[ScalaResolveResult] = {
    val processor = new CompletionProcessor(StdKinds.methodRef, place)
    processor.processType(`type`, place)
    processor.candidatesS
  }

  private final case class ExtensionMethodCandidate(resolveResult: ScalaResolveResult,
                                                    elementToImport: ScFunction,
                                                    classToImport: ScObject)
    extends GlobalMemberResult(resolveResult, elementToImport, classToImport) {

    override protected def patchItem(lookupItem: ScalaLookupItem): Unit = {
      lookupItem.usedImportStaticQuickfix = true
    }
  }
}

object ExtensionMethodsFinder {
  def apply(qualifier: ScExpression): Option[ExtensionMethodsFinder] = {
    val qualifierType = qualifier.getTypeWithoutImplicits().toOption

    qualifierType.map(new ExtensionMethodsFinder(_, qualifier))
  }
}
