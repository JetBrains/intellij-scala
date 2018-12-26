package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{AfterUpdate, ScSubstitutor, Update}
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScParameterizedType, ScType, ScTypeExt, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectContext

final case class JavaArrayType(argument: ScType) extends ValueType {

  override implicit def projectContext: ProjectContext = argument.projectContext

  def getParameterizedType(implicit elementScope: ElementScope): Option[ValueType] = {
    elementScope.getCachedClasses("scala.Array").collect {
      case clazz: ScClass => clazz
    }.find(_.getTypeParameters.length == 1)
      .map(ScalaType.designator)
      .map(ScParameterizedType(_, Seq(argument)))
  }

  override def updateSubtypes(substitutor: ScSubstitutor, visited: Set[ScType]): JavaArrayType = {
    JavaArrayType(argument.recursiveUpdateImpl(substitutor, visited))
  }

  override def updateSubtypesVariance(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant)
                                     (implicit visited: Set[ScType]): ScType =
    JavaArrayType(argument.recursiveVarianceUpdate(update, Invariant))

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult =
    `type` match {
      case JavaArrayType(thatArgument) => argument.equiv(thatArgument, constraints, falseUndef)
      case ParameterizedType(designator, arguments) if arguments.length == 1 =>
        designator.extractClass match {
          case Some(td) if td.qualifiedName == "scala.Array" => argument.equiv(arguments.head, constraints, falseUndef)
          case _ => ConstraintsResult.Left
        }
      case _ => ConstraintsResult.Left
    }

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitJavaArrayType(this)

  override def typeDepth: Int = argument.typeDepth
}
