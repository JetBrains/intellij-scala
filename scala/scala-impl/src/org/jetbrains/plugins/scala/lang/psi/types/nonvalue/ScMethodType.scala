package org.jetbrains.plugins.scala
package lang
package psi
package types
package nonvalue

final case class ScMethodType(result: ScType, params: Seq[Parameter], isImplicit: Boolean)
                             (implicit val elementScope: ElementScope) extends NonValueType {

  implicit def projectContext: project.ProjectContext = elementScope.projectContext

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitMethodType(this)

  override def typeDepth: Int = result.typeDepth

  def inferValueType: api.ValueType = {
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
