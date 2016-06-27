package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScTypeExt, ScUndefinedSubstitutor, ScalaType, api}

import scala.collection.immutable.HashSet

case class JavaArrayType(argument: ScType)(implicit val typeSystem: TypeSystem) extends ValueType with TypeInTypeSystem {

  def getParameterizedType(project: Project, scope: GlobalSearchScope) =
    ScalaPsiManager.instance(project).getCachedClasses(scope, "scala.Array")
      .find {
        clazz => clazz.isInstanceOf[ScClass] && clazz.getTypeParameters.length == 1
      }
      .map {
        designator => ScParameterizedType(ScalaType.designator(designator), Seq(argument))
      }

  override def removeAbstracts = JavaArrayType(argument.removeAbstracts)

  override def recursiveUpdate(update: ScType => (Boolean, ScType),
                               visited: HashSet[ScType]): ScType = {
    if (visited.contains(this)) {
      return update(this) match {
        case (true, res) => res
        case _ => this
      }
    }
    update(this) match {
      case (true, res) => res
      case _ => JavaArrayType(argument.recursiveUpdate(update, visited + this))
    }
  }

  override def recursiveVarianceUpdateModifiable[T](data: T,
                                                    update: (ScType, Int, T) => (Boolean, ScType, T),
                                                    variance: Int = 1) =
    update(this, variance, data) match {
      case (true, res, _) => res
      case (_, _, newData) =>
        JavaArrayType(argument.recursiveVarianceUpdateModifiable(newData, update, 0))
    }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: api.TypeSystem) =
    `type` match {
      case JavaArrayType(thatArgument) => argument.equiv(thatArgument, substitutor, falseUndef)
      case ParameterizedType(designator, arguments) if arguments.length == 1 =>
        designator.extractClass() match {
          case Some(td) if td.qualifiedName == "scala.Array" => argument.equiv(arguments.head, substitutor, falseUndef)
          case _ => (false, substitutor)
        }
      case _ => (false, substitutor)
    }

  override def visitType(visitor: TypeVisitor) = visitor.visitJavaArrayType(this)

  override def typeDepth = argument.typeDepth
}
