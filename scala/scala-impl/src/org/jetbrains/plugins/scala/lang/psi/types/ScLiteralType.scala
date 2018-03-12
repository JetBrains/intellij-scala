package org.jetbrains.plugins.scala.lang.psi.types
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeVisitor, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.project.ProjectContext

class ScLiteralType private (val literalText: String, private val project: ProjectContext, val wideType: ScType) extends ValueType {
  override def visitType(visitor: TypeVisitor): Unit = visitor.visitLiteralType(this)

  override implicit def projectContext: ProjectContext = project

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ScLiteralType => other.literalText == literalText
      case _ => false
    }
  }

  override def hashCode(): Int = literalText.hashCode
}

object ScLiteralType {
  import scala.collection.concurrent._
  val cache: Map[(String, Project), ScLiteralType] = {
    import scala.collection.JavaConverters._
    new java.util.concurrent.ConcurrentHashMap[(String, Project), ScLiteralType].asScala
  }

  def apply(typeElement: ScLiteralTypeElement): ScLiteralType = {
    apply(typeElement.getLiteralText, typeElement.projectContext, ScLiteralImpl.getLiteralType(typeElement.getLiteralNode, typeElement))
  }

  def apply(literalText :String, project: ProjectContext, wideType: ScType): ScLiteralType = {
    cache.putIfAbsent((literalText, project), new ScLiteralType(literalText, project, wideType))
    cache((literalText, project))
  }

  def widen(aType: ScType): ScType = aType match {
    case lit: ScLiteralType => lit.wideType
    case other => other
  }

  def widenRecursive(aType: ScType): ScType = aType.recursiveUpdate{
    case lit: ScLiteralType => ReplaceWith(widen(lit))
    case p: ScParameterizedType =>
      p.designator match {
        case ScDesignatorType(des) => des match {
          case typeDef: ScTypeDefinition =>
            import org.jetbrains.plugins.scala.lang.psi.types.api._
            val newDes = ScParameterizedType(widenRecursive(p.designator), (typeDef.typeParameters zip p.typeArguments).map{
              case (param, arg) if param.upperBound.exists(_.conforms(Singleton(p.projectContext))) => arg
              case (_, arg) => widenRecursive(arg)
            })
            ReplaceWith(newDes)
          case _ => Stop
        }
        case _ => Stop
      }
    case _ => ProcessSubtypes
  }
}