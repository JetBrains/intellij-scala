package org.jetbrains.plugins.scala
package lang
package psi
package types
package nonvalue

import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGenericCall, ScMethodCall, ScReferenceExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import scala.annotation.tailrec

final case class ScMethodType(result: ScType, params: collection.Seq[Parameter], isImplicit: Boolean)
                             (implicit val elementScope: ElementScope) extends NonValueType {

  override implicit def projectContext: project.ProjectContext = elementScope.projectContext

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitMethodType(this)

  override def typeDepth: Int = result.typeDepth

  override def inferValueType: api.ValueType = {
    api.FunctionType(result.inferValueType, params.map(p => {
      val inferredParamType = p.paramType.inferValueType
      if (!p.isRepeated) inferredParamType
      else inferredParamType.tryWrapIntoSeqType
    }))
  }

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
    case r: ScReferenceExpression => r.bind().exists(_.problems.exists(_.isInstanceOf[MissedParametersClause]))
    case call: ScMethodCall if !call.getParent.isInstanceOf[ScUnderscoreSection] => call.deepestInvokedExpr match {
      case method: ScReferenceExpression => method.bind().map(_.element) match {
        case Some(definition: ScFunctionDefinition) =>
          definition.paramClauses.clauses.takeWhile(!_.isImplicit).length > call.argumentListCount
        case _ => false
      }
      case _ => false
    }
    case i: MethodInvocation => i.applicationProblems.exists(_.isInstanceOf[MissedValueParameter]) // Infix Expression
    case c: ScGenericCall => hasMethodType(c.referencedExpr)
    case _ => false
  }
}
