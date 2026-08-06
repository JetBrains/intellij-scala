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

    leadingTypeParametersClause.toSeq.map(TypeClause) ++ clausesFromParameters
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

  private def contextBoundIsUsedInTypeParameterClause(
    clause:  ScTypeParamClause,
    tparams: Seq[ScTypeParam]
  ): Boolean = {
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
      false
    else {
      clause.accept(visitor)
      boundUsageFound
    }
  }

  def insertSyntheticSignatureClauses(
    parameters:       ScParameters,
    effectiveClauses: Seq[ScParameterClause],
    signatureClauses: Seq[ScSignatureClause],
    typeParameters:   Seq[ScTypeParam],
    isClassParameter: Boolean
  ): Seq[ScSignatureClause] = {
    import ScSignatureClause._

    effectiveClauses.foreach(setPrependedContextBoundTypeParameters(_, Seq.empty))

    val clausesTypeParameters = signatureClauses
      .flatMap {
        case TypeClause(clause) => clause.typeParameters
        case TermClause(_)      => Seq.empty
      }

    val clausesInSignature = signatureClauses.flatMap {
      case TypeClause(_)      => Seq.empty
      case TermClause(clause) => Seq(clause)
    }

    // Type params not represented in the local signature clauses (e.g. extension owner type params)
    // are treated as in scope from the beginning.
    val pendingTypeParameters = scala.collection.mutable.ArrayBuffer.from(
      typeParameters.filterNot(clausesTypeParameters.contains)
    )

    val orphanClauses = effectiveClauses.filterNot(clausesInSignature.contains)
    val resultClauses = scala.collection.mutable.ArrayBuffer.empty[ScSignatureClause]
    resultClauses ++= orphanClauses.map(TermClause)

    def flushPendingTypeParameters(): Unit = {
      if (pendingTypeParameters.nonEmpty) {
        ScalaPsiUtil.syntheticParamClause(
          pendingTypeParameters.toSeq,
          parameters,
          isClassParameter,
          hasImplicit = false
        ).foreach(clause => resultClauses += TermClause(clause))
        pendingTypeParameters.clear()
      }
    }

    signatureClauses.foreach {
      case TypeClause(clause) =>
        if (contextBoundIsUsedInTypeParameterClause(clause, pendingTypeParameters.toSeq))
          flushPendingTypeParameters()
        resultClauses += TypeClause(clause)
        pendingTypeParameters ++= clause.typeParameters

      case TermClause(clause) =>
        val hasPendingBoundUsageInClause =
          contextBoundUsageInParameterListIndex(Seq(clause), pendingTypeParameters.toSeq) != -1

        if (hasPendingBoundUsageInClause)
          flushPendingTypeParameters()
        else if (clause.isImplicit && pendingTypeParameters.nonEmpty) {
          setPrependedContextBoundTypeParameters(clause, pendingTypeParameters.toSeq)
          pendingTypeParameters.clear()
        }
        resultClauses += TermClause(clause)
    }

    flushPendingTypeParameters()
    resultClauses.toSeq
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
