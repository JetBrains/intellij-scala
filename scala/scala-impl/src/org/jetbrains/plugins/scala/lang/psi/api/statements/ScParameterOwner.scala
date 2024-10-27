package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiElement, ResolveState}
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPathElement, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

trait ScParameterOwner extends ScalaPsiElement {
  def parameters: Seq[ScParameter]
  def clauses: Option[ScParameters]
  final def allClauses: Seq[ScParameterClause] =
    clauses match {
      case Some(x) => x.clauses
      case None    => Seq.empty
    }
}

object ScParameterOwner {
  trait WithContextBounds extends ScParameterOwner with ScTypeParametersOwner {
    def effectiveParameterClauses: Seq[ScParameterClause] =
      cachedInUserData(
        "effectiveParameterClauses",
        this,
        BlockModificationTracker(this)
      ) {
        clauses
          .map(
            parameters =>
              insertSyntheticParameterClause(
                parameters,
                parameters.clauses,
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
      for {
        clause <- effectiveParameterClauses
        param  <- clause.effectiveParameters
      } {
        ProgressManager.checkCanceled()
        if (!processor.execute(param, state))
          return false
      }
      true
    }

    def processNamedContextBounds(
      typeParameters: Seq[ScTypeParam],
      processor:      PsiScopeProcessor,
      state:          ResolveState
    ): Boolean = {
      val bounds = boundNames(typeParameters)
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
          boundUsageFound =
            contextBoundsNames.exists(name => ref.qualifier.exists(_.getText == name))
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

  def insertSyntheticParameterClause(
    parameters:       ScParameters,
    clauses:          Seq[ScParameterClause],
    typeParameters:   Seq[ScTypeParam],
    isClassParameter: Boolean
  ): Seq[ScParameterClause] = {
    val clauseIdxWithBoundUsage = contextBoundUsageInParameterListIndex(clauses, typeParameters)

    val syntheticClause =
      ScalaPsiUtil.syntheticParamClause(
        typeParameters,
        parameters,
        isClassParameter,
        parameters.clauses.exists(_.isImplicitOrUsing)
      )

    if (clauseIdxWithBoundUsage == -1)
      clauses ++ syntheticClause
    else {
      val (before, after) = clauses.splitAt(clauseIdxWithBoundUsage)
      before ++ syntheticClause ++ after
    }
  }
}
