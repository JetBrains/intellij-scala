package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.Update
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

  override def updateSubtypes(updates: Seq[Update], visited: Set[ScType]): JavaArrayType = {
    JavaArrayType(argument.recursiveUpdateImpl(updates, visited))
  }

  override def recursiveVarianceUpdate(update: (ScType, Variance) => (Boolean, ScType),
                                       variance: Variance = Covariant,
                                       revertVariances: Boolean = false): ScType =
    update(this, variance) match {
      case (true, res) => res
      case (_, _) =>
        JavaArrayType(argument.recursiveVarianceUpdate(update, Invariant))
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
