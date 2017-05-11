package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScTypeExt, ScUndefinedSubstitutor, ScalaType}
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

  override def updateSubtypes(update: (ScType) => (Boolean, ScType), visited: Set[ScType]): JavaArrayType = {
    JavaArrayType(argument.recursiveUpdate(update, visited))
  }

  override def recursiveVarianceUpdateModifiable[T](data: T,
                                                    update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1): ScType =
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        JavaArrayType(argument.recursiveVarianceUpdateModifiable(newData, update, 0))
    }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) =
    `type` match {
      case JavaArrayType(thatArgument) => argument.equiv(thatArgument, substitutor, falseUndef)
      case ParameterizedType(designator, arguments) if arguments.length == 1 =>
        designator.extractClass match {
          case Some(td) if td.qualifiedName == "scala.Array" => argument.equiv(arguments.head, substitutor, falseUndef)
          case _ => (false, substitutor)
        }
      case _ => (false, substitutor)
    }

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitJavaArrayType(this)

  override def typeDepth: Int = argument.typeDepth
}
