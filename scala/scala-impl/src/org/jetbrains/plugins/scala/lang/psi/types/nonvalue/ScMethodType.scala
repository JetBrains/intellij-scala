package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, MissedParametersClause, MissedValueParameter, ScType, ScalaTypeVisitor}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

final case class ScMethodType(result: ScType,
                              // TODO: we should also be able to express the absence of parameter clauses in method
                              //  to distinguish between def foo: String = ??? and def foo(): String
                              params: Seq[Parameter],
                              isImplicit: Boolean)
                             (implicit val elementScope: ElementScope) extends NonValueType {

  override implicit def projectContext: ProjectContext = elementScope.projectContext

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitMethodType(this)

  override def typeDepth: Int = result.typeDepth

  override def inferValueType: ValueType =
    FunctionType(result.inferValueType, params.map(p => {
      val inferredParamType = p.paramType.inferValueType
      if (!p.isRepeated) inferredParamType
      else               inferredParamType.tryWrapIntoSeqType
    }))

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    var lastConstraints = constraints
    r match {
      case m: ScMethodType =>
        if (m.params.length != params.length) return ConstraintsResult.Left
        var t = m.result.equiv(result, lastConstraints, falseUndef)
        if (t.isLeft) return ConstraintsResult.Left
        lastConstraints = t.constraints
        var i = 0
        while (i < params.length) {
          //todo: Seq[Type] instead of Type*
          if (params(i).isRepeated != m.params(i).isRepeated) return ConstraintsResult.Left
          t = params(i).paramType.equiv(m.params(i).paramType, lastConstraints, falseUndef)
          if (t.isLeft) return ConstraintsResult.Left
          lastConstraints = t.constraints
          i = i + 1
        }
        lastConstraints
      case _ => ConstraintsResult.Left
    }
  }
}

object ScMethodType {
  // A safe & simple workaround for https://youtrack.jetbrains.com/issue/SCL-16431 and https://youtrack.jetbrains.com/issue/SCL-15354
  // TODO Actually infer method types
  @tailrec def hasMethodType(e: ScExpression): Boolean = e match {
    case r: ScReferenceExpression => r.bind().exists(_.problems.exists(_.is[MissedParametersClause]))
    case call: ScMethodCall if !call.getParent.is[ScUnderscoreSection] => call.deepestInvokedExpr match {
      case method: ScReferenceExpression => method.bind().map(_.element) match {
        case Some(definition: ScFunctionDefinition) =>
          definition.paramClauses.clauses.takeWhile(!_.isImplicit).length > call.argumentListCount
        case _ => false
      }
      case _ => false
    }
    case p: ScPostfixExpr => p.operation.bind().exists(_.problems.exists(_.is[MissedParametersClause]))
    case i: MethodInvocation => i.applicationProblems.exists(_.is[MissedValueParameter]) // Infix Expression
    case c: ScGenericCall => hasMethodType(c.referencedExpr)
    case _ => false
  }
}
