package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScParameterOwner, ScSignatureClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.light.ScPrimaryConstructorWrapper

import scala.collection.immutable.ArraySeq

/**
 * @note Secondary constructor is represented by [[org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction]]
 */
trait ScPrimaryConstructor extends ScMember with ScMethodLike {

  /**
   *  @return has access modifier
   */
  def hasModifier: Boolean

  def getClassNameText: String

  override def parameterList: ScParameters

  override def clauses: Option[ScParameters] = Option(parameterList)

  override def parameters: Seq[ScClassParameter] = parameterList.clauses.flatMap(_.unsafeClassParameters)

  override def containingClass: ScTypeDefinition = getParent.asInstanceOf[ScTypeDefinition]

  /**
   * return only parameters, which are additionally members.
   */
  def valueParameters: Seq[ScClassParameter] = parameters.filter((p: ScClassParameter) => p.isVal || p.isVar)

  /**
   * View and context bounds generate an additional implicit parameter section.
   *
   * In addition, all classes must have one non-implicit parameter list. If this is not declared in the code,
   * it is assumed by the compiler (`NamerOps.normalizeIfConstructor`), which adds an empty clause, after evidence:
   *  - in front, if the first clause is an `implicit` clause, e.g. `class C(implicit x: Int)` is `C()(implicit x: Int)`
   *  - at the end, if all clauses are `using` clauses, e.g. `class C(using x: Int)` is `C(using x: Int)()`
   */
  override def effectiveSignatureClauses: Seq[ScSignatureClause] =
    cachedInUserData("effectiveSignatureClauses", this, BlockModificationTracker(this)) {
      val withEvidence = ScParameterOwner.insertSyntheticSignatureClauses(
        parameterList,
        parameterList.clauses,
        signatureClauses,
        containingClass.typeParameters,
        isClassParameter = true
      )

      // The kind of a clause is the kind of its first parameter, which is evidence if it's merged into the clause,
      // e.g. with Scala 3.3, `class C[A: Show](using x: Int)` is `C()(implicit ev: Show[A], x: Int)`.
      // `effectiveParameters` can't be used here, it depends on these clauses.
      def hasMergedEvidence(clause: ScParameterClause): Boolean =
        ScParameterOwner.prependedContextBoundTypeParameters(clause).nonEmpty

      def mergedEvidenceIsUsing(clause: ScParameterClause): Boolean =
        ScParameterOwner.isUsingEvidence(Option(ScSignatureClause.TermClause(clause)), clause)

      def startsWithImplicit(clause: ScParameterClause): Boolean =
        if (hasMergedEvidence(clause)) !mergedEvidenceIsUsing(clause)
        else clause.parameters.nonEmpty && clause.hasImplicitKeyword

      def startsWithUsing(clause: ScParameterClause): Boolean =
        if (hasMergedEvidence(clause)) mergedEvidenceIsUsing(clause)
        else clause.parameters.nonEmpty && clause.hasUsingKeyword

      val termClauses = withEvidence.collect { case ScSignatureClause.TermClause(clause) => clause }

      def emptyClause: ScSignatureClause = {
        val clause = ScalaPsiElementFactory.createEmptyClassParamClauseWithContext(parameterList)
        clause.putUserData(ScPrimaryConstructor.ImpliedEmptyClauseKey, java.lang.Boolean.TRUE)
        ScSignatureClause.TermClause(clause)
      }

      if (termClauses.headOption.exists(startsWithImplicit)) emptyClause +: withEvidence
      else if (termClauses.forall(startsWithUsing))          withEvidence :+ emptyClause
      else                                                   withEvidence
    }

  /**
   * The first parameter section, as in the code: the implied empty clause, if there is one,
   * otherwise the first clause (see [[effectiveSignatureClauses]])
   */
  def effectiveFirstParameterSection: Seq[ScClassParameter] = {
    val clauses = effectiveParameterClauses
    clauses.find(ScPrimaryConstructor.isImpliedEmptyClause).getOrElse(clauses.head).unsafeClassParameters
  }

  def getFunctionWrappers: Seq[ScPrimaryConstructorWrapper] = _getFunctionWrappers()

  private val _getFunctionWrappers = cached("getFunctionWrappers", BlockModificationTracker(this), () => {
    val builder = ArraySeq.newBuilder[ScPrimaryConstructorWrapper]

    for {
      first <- parameterList.clauses.headOption
      if first.hasRepeatedParam
      if hasAnnotation("scala.annotation.varargs")
    } builder += new ScPrimaryConstructorWrapper(this, isJavaVarargs = true)

    builder += new ScPrimaryConstructorWrapper(this)
    builder.result()
  })
}

object ScPrimaryConstructor {
  private val ImpliedEmptyClauseKey: Key[java.lang.Boolean] = Key.create("ScPrimaryConstructor.implied.empty.clause")

  /** Whether `clause` is the empty clause assumed by the compiler, see [[ScPrimaryConstructor.effectiveSignatureClauses]] */
  def isImpliedEmptyClause(clause: ScParameterClause): Boolean =
    clause.getUserData(ImpliedEmptyClauseKey) == java.lang.Boolean.TRUE

  object ofTypeDefinition {
    def unapply(pc: ScPrimaryConstructor): Some[ScTypeDefinition] =
      Some(pc.containingClass)
  }
  object ofClass {
    def unapply(pc: ScPrimaryConstructor): Option[ScClass] = {
      pc.containingClass match {
        case c: ScClass => Some(c)
        case _          => None
      }
    }
  }
}
