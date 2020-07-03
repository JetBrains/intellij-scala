package org.jetbrains.plugins.scala
package lang
package completion
package global

import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ContainingClass}
import org.jetbrains.plugins.scala.lang.completion.handlers.ScalaImportingInsertHandler
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.implicits._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

private[completion] final class ExtensionMethodsFinder(private val originalType: ScType,
                                                       override protected val place: ScExpression)
  extends GlobalMembersFinder(place, accessAll = true) {

  private lazy val originalTypeMemberNames: collection.Set[String] = candidatesForType(originalType).map(_.name)

  override protected def candidates: Iterable[GlobalMemberResult] = for {
    (GlobalImplicitConversion(classToImport, elementToImport), resultType) <- ImplicitConversionCache(place.getProject)
      .getPossibleConversions(place)

    resolveResult <- candidatesForType(resultType)
    if !originalTypeMemberNames.contains(resolveResult.name)
  } yield ExtensionMethodCandidate(resolveResult, classToImport, elementToImport)

  private def candidatesForType(`type`: ScType): collection.Set[ScalaResolveResult] =
    CompletionProcessor.variants(`type`, place)

  private final case class ExtensionMethodCandidate(override val resolveResult: ScalaResolveResult,
                                                    override val classToImport: ScObject,
                                                    elementToImport: ScFunction)
    extends GlobalMemberResult(resolveResult, classToImport) {

    override protected def createInsertHandler: ScalaImportingInsertHandler = new ScalaImportingInsertHandler(classToImport) {

      override protected def qualifyAndImport(reference: ScReferenceExpression): Unit = for {
        ContainingClass(ClassQualifiedName(_)) <- Option(elementToImport.nameContext)
        holder = ScImportsHolder(reference)
      } holder.addImportForPsiNamedElement(
        elementToImport,
        null,
        Some(containingClass)
      )
    }
  }
}

