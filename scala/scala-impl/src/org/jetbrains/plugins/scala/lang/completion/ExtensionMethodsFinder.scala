package org.jetbrains.plugins.scala.lang.completion

import com.intellij.psi.{PsiClass, PsiFile, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.targetTypeAndSubstitutor
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitConversionCache, ImplicitConversionProcessor}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

private final class ExtensionMethodsFinder(originalType: ScType, place: ScExpression) extends GlobalMembersFinder {
  lazy val originalTypeMemberNames: collection.Set[String] = candidatesForType(originalType).map(_.name)

  override protected def candidates: Iterable[GlobalMemberResult] = for {
    (key, conversionData) <- ImplicitConversionCache.getOrScheduleUpdate(place.elementScope)

    if ImplicitConversionProcessor.applicable(key.function, place)

    (resultType, _)       <- targetTypeAndSubstitutor(conversionData, originalType, place).toIterable
    item                  <- extensionCandidates(key.function, key.containingObject, resultType)
  } yield item

  private def extensionCandidates(conversion: ScFunction, conversionContainer: ScObject, resultType: ScType): Iterable[ExtensionMethodCandidate] = {
    val newCandidates =
      candidatesForType(resultType)
        .filterNot(c => originalTypeMemberNames.contains(c.name))

    newCandidates.map {
      ExtensionMethodCandidate(_, conversion, conversionContainer)
    }
  }

  private def candidatesForType(tp: ScType): collection.Set[ScalaResolveResult] = {
    val processor = new CompletionProcessor(StdKinds.methodRef, place)
    processor.processType(tp, place)
    processor.candidatesS
  }

  private final case class ExtensionMethodCandidate(override val resolveResult: ScalaResolveResult,
                                                    override val elementToImport: ScFunction,
                                                    override val classToImport: ScObject) extends GlobalMemberResult {

    override protected val isOverloadedForClassName = false
    override protected val containingClass: PsiClass = null

    override def createLookupItem(originalFile: PsiFile,
                                  elements: Set[PsiNamedElement]): Option[ScalaLookupItem] = {
      super.createLookupItem(originalFile, elements).map { lookupItem =>
        lookupItem.usedImportStaticQuickfix = true
        lookupItem
      }
    }
  }
}

private object ExtensionMethodsFinder {
  def apply(qualifier: ScExpression): Option[ExtensionMethodsFinder] = {
    val qualifierType = qualifier.getTypeWithoutImplicits().toOption

    qualifierType.map(new ExtensionMethodsFinder(_, qualifier))
  }
}
