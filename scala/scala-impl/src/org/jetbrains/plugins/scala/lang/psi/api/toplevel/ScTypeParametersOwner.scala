package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScInterleavedClausesOwner, ScSignatureClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScTypeParametersOwner extends ScalaPsiElement {

  def typeParameters: Seq[ScTypeParam] = _typeParameters()

  private val _typeParameters = cached("typeParameters", ModTracker.anyScalaPsiChange, () => {
    typeParameterClauses.flatMap(_.typeParameters)
  })

  /**
   * All physical type parameter clauses attached to this owner in lexical order.
   * For Scala 3 method signatures this includes interleaved clauses from the parameter section.
   */
  def typeParameterClauses: Seq[ScTypeParamClause] = _typeParameterClauses()

  private val _typeParameterClauses = cached("typeParameterClauses", ModTracker.anyScalaPsiChange, () => {
    val leadingClause = leadingTypeParametersClause

    val interleavedClauses = this match {
      case owner: ScInterleavedClausesOwner =>
        owner.signatureClauses.collect {
          case ScSignatureClause.TypeClause(clause) if !leadingClause.contains(clause) => clause
        }
      case _ => Seq.empty
    }

    leadingClause.toSeq ++ interleavedClauses
  })

  /**
   * Type parameter clause that is a direct child of this owner.
   * For function-like signatures this corresponds to the leading clause.
   */
  def leadingTypeParametersClause: Option[ScTypeParamClause] = {
    this.withGreenStub(
      stub => Option(stub.findChildStubByElementType(ScalaElementType.TYPE_PARAM_CLAUSE))
        .map(_.getPsi.asInstanceOf[ScTypeParamClause]),
      () => Option(getNode).flatMap(_ => findChild[ScTypeParamClause])
    )
  }

  /**
   * Backward-compatible alias for the leading type parameter clause.
   */
  def typeParametersClause: Option[ScTypeParamClause] =
    leadingTypeParametersClause

  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent != null) {
      val typeParametersInScope = this match {
        case owner: ScInterleavedClausesOwner => owner.typeParametersInScopeFor(place)
        case _                                => typeParameters
      }

      val it = typeParametersInScope.iterator
      while (it.hasNext) {
        ProgressManager.checkCanceled()
        if (!processor.execute(it.next(), state))
          return false
      }
    }
    true
  }
}
