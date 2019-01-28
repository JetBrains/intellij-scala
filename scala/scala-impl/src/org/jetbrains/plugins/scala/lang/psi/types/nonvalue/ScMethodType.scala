package org.jetbrains.plugins.scala
package lang.psi.types.nonvalue

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

final case class ScMethodType(returnType: ScType, params: Seq[Parameter], isImplicit: Boolean)
                             (implicit val elementScope: ElementScope) extends NonValueType {

  implicit def projectContext: ProjectContext = elementScope.projectContext

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitMethodType(this)

  override def typeDepth: Int = returnType.typeDepth

  def inferValueType: ValueType = {
    FunctionType(returnType.inferValueType, params.map(p => {
      val inferredParamType = p.paramType.inferValueType
      if (!p.isRepeated) inferredParamType
      else inferredParamType.tryWrapIntoSeqType
    }))
  }

  override def updateSubtypes(substitutor: ScSubstitutor, variance: Variance)
                             (implicit visited: Set[ScType]): ScType = {

    def updateParameterType(tp: ScType) = tp.recursiveUpdateImpl(substitutor, -variance, isLazySubtype = true)
    def updateParameter(p: Parameter): Parameter = p.copy(
      paramType = updateParameterType(p.paramType),
      expectedType = updateParameterType(p.expectedType),
      defaultType = p.defaultType.map(updateParameterType)
    )
    ScMethodType(
      returnType.recursiveUpdateImpl(substitutor, variance),
      params.map(updateParameter),
      isImplicit)
  }

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    var lastConstraints = constraints
    r match {
      case m: ScMethodType =>
        if (m.params.length != params.length) return ConstraintsResult.Left
        var t = m.returnType.equiv(returnType, lastConstraints, falseUndef)
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
