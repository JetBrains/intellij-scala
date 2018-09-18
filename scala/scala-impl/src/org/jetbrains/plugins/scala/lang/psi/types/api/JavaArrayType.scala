package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.{AfterUpdate, Update}
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintsResult, ScParameterizedType, ScType, ScTypeExt, ConstraintSystem, ScalaType}
import org.jetbrains.plugins.scala.project.ProjectContext

case class JavaArrayType(argument: ScType) extends ValueType {

  override implicit def projectContext: ProjectContext = argument.projectContext

  def getParameterizedType(implicit elementScope: ElementScope): Option[ValueType] = {
    elementScope.getCachedClasses("scala.Array").collect {
      case clazz: ScClass => clazz
    }.find(_.getTypeParameters.length == 1)
      .map(ScalaType.designator)
      .map(ScParameterizedType(_, Seq(argument)))
  }

  override def removeAbstracts = JavaArrayType(argument.removeAbstracts)

  override def updateSubtypes(updates: Array[Update], index: Int, visited: Set[ScType]): JavaArrayType = {
    JavaArrayType(argument.recursiveUpdateImpl(updates, index, visited))
  }

  override def updateSubtypesVariance(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant,
                                      revertVariances: Boolean = false)
                                     (implicit visited: Set[ScType]): ScType =
    JavaArrayType(argument.recursiveVarianceUpdate(update, Invariant))

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult =
    `type` match {
      case JavaArrayType(thatArgument) => argument.equiv(thatArgument, constraints, falseUndef)
      case ParameterizedType(designator, arguments) if arguments.length == 1 =>
        designator.extractClass match {
          case Some(td) if td.qualifiedName == "scala.Array" => argument.equiv(arguments.head, constraints, falseUndef)
          case _ => ConstraintsResult.Failure
        }
      case _ => ConstraintsResult.Failure
    }

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitJavaArrayType(this)

  override def typeDepth: Int = argument.typeDepth
}
