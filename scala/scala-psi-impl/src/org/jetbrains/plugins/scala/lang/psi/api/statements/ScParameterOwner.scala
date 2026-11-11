package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker, cached, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

trait ScParameterOwner extends ScalaPsiElement {
  def parameters: Seq[ScParameter]
  def clauses: Option[ScParameters]
  final def allClauses: Seq[ScParameterClause] =
    clauses match {
      case Some(x) => x.clauses
      case None    => Seq.empty
    }
}

trait ScInterleavedClausesOwner extends ScParameterOwner with ScTypeParametersOwner {
  import ScSignatureClause._

  def signatureClauses: Seq[ScSignatureClause] = _signatureClauses()

  def effectiveSignatureClauses: Seq[ScSignatureClause] = signatureClauses

  private val _signatureClauses = cached("signatureClauses", ModTracker.anyScalaPsiChange, () => {
    val clausesFromParameters = clauses.toSeq.flatMap { parameters =>
      val clausesInLexicalOrder = parameters.stubOrPsiChildren.collect {
        case clause: ScTypeParamClause => TypeClause(clause)
        case clause: ScParameterClause => TermClause(clause)
      }.toSeq

      clausesInLexicalOrder
    }

    leadingTypeParametersClause.toSeq.map(TypeClause.apply) ++ clausesFromParameters
  })

  def typeParametersInScopeFor(place: PsiElement): Seq[ScTypeParam] = {
    val clauses = signatureClauses

    val containingClauseIndex = clauses.indexWhere {
      case TypeClause(clause) => PsiTreeUtil.isAncestor(clause, place, false)
      case TermClause(clause) => PsiTreeUtil.isAncestor(clause, place, false)
    }

    val clausesInScope =
      if (containingClauseIndex == -1) clauses
      else {
        val beforeContaining = clauses.take(containingClauseIndex)
        clauses(containingClauseIndex) match {
          case typeClause: TypeClause => beforeContaining :+ typeClause
          case _                      => beforeContaining
        }
      }

    clausesInScope.flatMap {
      case TypeClause(clause) => clause.typeParameters
      case _                  => Seq.empty
    }
  }
}

object ScParameterOwner {
  private val PrependedContextBoundTypeParametersKey: Key[Seq[ScTypeParam]] =
    Key.create("scala.prepended.context.bound.type.parameters")

  trait WithContextBounds extends ScInterleavedClausesOwner {
    def effectiveParameterClauses: Seq[ScParameterClause] =
      effectiveSignatureClauses.collect { case ScSignatureClause.TermClause(clause) => clause }

    override def effectiveSignatureClauses: Seq[ScSignatureClause] =
      cachedInUserData(
        "effectiveSignatureClauses",
        this,
        BlockModificationTracker(this)
      ) {
        clauses
          .map(
            parameters =>
              insertSyntheticSignatureClauses(
                parameters,
                parameters.clauses,
                signatureClauses,
                typeParameters,
                isClassParameter = false
              )
          )
          .getOrElse(Seq.empty)
      }

    def processParameters(
      processor: PsiScopeProcessor,
      state:     ResolveState
    ): Boolean = {
      effectiveParameterClauses.forall { clause =>
        clause.effectiveParameters.forall { param =>
          ProgressManager.checkCanceled()
          processor.execute(param, state)
        }
      }
    }

    def processNamedContextBounds(
      processor: PsiScopeProcessor,
      state:     ResolveState,
      place:     PsiElement
    ): Boolean = {
      val bounds = boundNames(typeParametersInScopeFor(place))
      for {
        clause <- effectiveParameterClauses
        param <- clause.effectiveParameters
        if bounds.contains(param.name)
      } {
        ProgressManager.checkCanceled()
        if (!processor.execute(param, state))
          return false
      }
      true
    }
  }

  private def boundNames(typeParameters: Seq[ScTypeParam]): Seq[String] =
    for {
      tparam    <- typeParameters
      boundName <- tparam.contextBounds.collect { case ScContextBound.Named(_, name) => name }
    } yield boundName

  def contextBoundUsageInParameterListIndex(clauses: Seq[ScParameterClause], tparams: Seq[ScTypeParam]): Int = {
    val contextBoundsNames = boundNames(tparams)
    var boundUsageFound    = false

    val visitor =
      new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReference): Unit = {
          boundUsageFound ||= contextBoundsNames.exists {
            name =>
              ref.textMatches(name) || ref.qualifier.exists(_.textMatches(name))
          }
        }
      }

    if (contextBoundsNames.isEmpty)
      -1
    else
      clauses.indexWhere {
        clause =>
          val params = clause.parameters

          params.foreach {
            param =>
              val te = param.typeElement
              te.foreach(_.accept(visitor))
          }

          boundUsageFound
      }
  }

  /**
   * Whether the evidence parameters of context (and view) bounds are `using` rather than `implicit` parameters.
   * It follows the compiler (`Desugar.elimContextBounds`):
   *  - since compiler 3.6, evidence takes the kind of the last parameter clause if it is an `implicit` or `using` clause
   *  - otherwise, it is `using` evidence since source version 3.6 (`future` with older compilers),
   *    and `implicit` evidence before that
   *
   * @param lastClause the last clause of the whole signature, before evidence is added
   */
  def isUsingEvidence(lastClause: Option[ScSignatureClause], context: PsiElement): Boolean =
    context.isInScala3File && (lastClause match {
      case Some(ScSignatureClause.TermClause(clause))
        if clause.isImplicit && context.scalaLanguageLevelOrDefault >= ScalaLanguageLevel.Scala_3_6 =>
        !clause.hasImplicitKeyword
      case _ =>
        context.effectiveLanguageLevel >= ScalaLanguageLevel.Scala_3_6
    })

  /**
   * Adds the evidence parameters of context (and view) bounds to the signature clauses.
   * It follows the compiler (`Desugar.addEvidenceParams`). The evidence of all type parameters forms one group,
   * which is placed as follows:
   *  1. before the first term clause that refers to a named context bound (`[A: Show as s](x: s.Out)`)
   *  2. merged in front of the parameters of the last clause, if that clause is an `implicit` or `using` clause
   *  3. as a new clause at the end of the signature otherwise
   *
   * As in the compiler, with interleaved type parameter clauses the group may precede the clause that declares
   * some of its type parameters, e.g. for `[A: Show as s](x: s.Out)[B: Ord](y: B)` (the compiler then rejects the code).
   *
   * Evidence is only ever merged into the owner's own clauses: clauses of an extension are shared by its methods.
   *
   * @param precedingClauses clauses preceding the owner's own clauses in the whole signature,
   *                         e.g. the clauses of an extension for an extension method
   * @param evidenceIsUsing  the evidence kind, if it is fixed by some other signature (for secondary constructors,
   *                         the kind of the primary constructor evidence); otherwise see [[isUsingEvidence]]
   */
  def insertSyntheticSignatureClauses(
    parameters:       ScParameters,
    effectiveClauses: Seq[ScParameterClause],
    signatureClauses: Seq[ScSignatureClause],
    typeParameters:   Seq[ScTypeParam],
    isClassParameter: Boolean,
    precedingClauses: Seq[ScSignatureClause] = Seq.empty,
    evidenceIsUsing:  Option[Boolean]        = None
  ): Seq[ScSignatureClause] = {
    import ScSignatureClause._

    effectiveClauses.foreach(setPrependedContextBoundTypeParameters(_, Seq.empty))

    val clausesInSignature = signatureClauses.collect { case TermClause(clause) => clause }

    // e.g. the empty clause, which is implied for a primary constructor
    val orphanClauses = effectiveClauses.filterNot(clausesInSignature.contains).map(TermClause.apply)
    val ownClauses    = orphanClauses ++ signatureClauses

    val withEvidence = typeParameters.filter(tp => tp.contextBounds.nonEmpty || tp.viewTypeElement.nonEmpty)
    if (withEvidence.isEmpty) return ownClauses

    lazy val isUsing =
      evidenceIsUsing.getOrElse(isUsingEvidence(ownClauses.lastOption.orElse(precedingClauses.lastOption), parameters))

    def syntheticClause: Option[TermClause] =
      ScalaPsiUtil.syntheticParamClause(
        withEvidence,
        parameters,
        isClassParameter,
        isUsing
      ).map(TermClause.apply)

    val referenceIndex = ownClauses.indexWhere {
      case TermClause(clause) => contextBoundUsageInParameterListIndex(Seq(clause), withEvidence) != -1
      case _                  => false
    }

    if (referenceIndex != -1) {
      val (before, after) = ownClauses.splitAt(referenceIndex)
      before ++ syntheticClause ++ after
    } else ownClauses.lastOption match {
      case Some(TermClause(last)) if last.isImplicit =>
        setPrependedContextBoundTypeParameters(last, withEvidence)
        ownClauses
      case _ =>
        ownClauses ++ syntheticClause
    }
  }

  private def setPrependedContextBoundTypeParameters(
    clause: ScParameterClause,
    prependedTypeParams: Seq[ScTypeParam]
  ): Unit = {
    val value =
      if (prependedTypeParams.nonEmpty) prependedTypeParams
      else null
    clause.putUserData(PrependedContextBoundTypeParametersKey, value)
  }

  private[psi] def prependedContextBoundTypeParameters(clause: ScParameterClause): Seq[ScTypeParam] =
    Option(clause.getUserData(PrependedContextBoundTypeParametersKey)).getOrElse(Seq.empty)
}
