package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.project.ProjectContext

final class ScLiteralType private(val value: ScLiteral.Value[_],
                                  val allowWiden: Boolean)
                                 (implicit project: Project)
  extends api.ValueType with LeafType {

  override implicit def projectContext: ProjectContext = project

  override def visitType(visitor: api.TypeVisitor): Unit = visitor.visitLiteralType(this)

  def wideType: ScType = value.wideType

  def blockWiden(): ScLiteralType = new ScLiteralType(value, allowWiden = false)

  override def equals(obj: Any): Boolean = obj match {
    case other: ScLiteralType => value == other.value
    case _ => false
  }

  override def hashCode: Int = value.hashCode
}

object ScLiteralType {

  import ScLiteral.{NullValue, Value}

  def apply(value: Value[_],
            allowWiden: Boolean = true)
           (implicit project: Project) =
    new ScLiteralType(value, allowWiden)

  def unapply(literalType: ScLiteralType): Some[(Value[_], Boolean)] =
    Some(literalType.value, literalType.allowWiden)

  def inferType[E <: ScalaPsiElement](literal: ScLiteral,
                                      allowWiden: Boolean = true): result.TypeResult = {
    implicit val project: Project = literal.getProject
    Value(literal) match {
      case null => result.Failure(ScalaBundle.message("wrong.psi.for.literal.type", literal.getText))
      case value@NullValue => Right(value.wideType)
      case value => Right(ScLiteralType(value, allowWiden))
    }
  }

  def widenRecursive(aType: ScType): ScType = {
    import recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}

    def isSingleton(param: ScTypeParam) = param.upperBound.exists {
      _.conforms(api.Singleton(param.projectContext))
    }

    def widenRecursiveInner(aType: ScType, visited: Set[ScParameterizedType]): ScType = aType.recursiveUpdate {
      case lit: ScLiteralType => ReplaceWith(lit.widen)
      case p: ScParameterizedType if visited(p) => Stop
      case p: ScParameterizedType =>
        p.designator match {
          case api.designator.ScDesignatorType(des) => des match {
            case typeDef: ScTypeDefinition =>
              val newDesignator = widenRecursiveInner(p.designator, visited + p)
              val newArgs = (typeDef.typeParameters zip p.typeArguments).map {
                case (param, arg) if isSingleton(param) => arg
                case (_, arg) => widenRecursiveInner(arg, visited + p)
              }
              val newDes = ScParameterizedType(newDesignator, newArgs)
              ReplaceWith(newDes)
            case _ => Stop
          }
          case _ => Stop
        }
      case _: ScCompoundType => Stop
      case _ => ProcessSubtypes
    }

    widenRecursiveInner(aType, Set.empty)
  }
}