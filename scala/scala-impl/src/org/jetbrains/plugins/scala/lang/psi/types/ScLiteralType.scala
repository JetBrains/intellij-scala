package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumSingletonCase
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.project.ProjectContext

final class ScLiteralType private(val value: ScLiteral.Value[_],
                                  val allowWiden: Boolean,
                                  // The psiElement, this literal type was created from
                                  // Especially useful in Scala3,
                                  // where String literals are used more and more
                                  // to reference real definitions.
                                  val psiElement: Option[PsiElement])
                                 (implicit project: Project)
  extends api.ValueType with LeafType {

  override implicit def projectContext: ProjectContext = project

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitLiteralType(this)

  def wideType: ScType = value.wideType

  def blockWiden: ScLiteralType = if (allowWiden) new ScLiteralType(value, allowWiden = false, psiElement = psiElement) else this

  override def equals(obj: Any): Boolean = obj match {
    case other: ScLiteralType => value == other.value
    case _                    => false
  }

  override def hashCode: Int = value.hashCode
}

object ScLiteralType {

  import ScLiteral.Value

  def apply(value: Value[_],
            allowWiden: Boolean = true,
            @Nullable psiElement: PsiElement = null)
           (implicit project: Project) =
    new ScLiteralType(value, allowWiden, Option(psiElement))

  def unapply(literalType: ScLiteralType): Some[(Value[_], Boolean)] =
    Some(literalType.value, literalType.allowWiden)

  def widenRecursive(`type`: ScType)(implicit context: Context): ScType = {
    import api._
    import recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}

    def isSingleton(param: ScTypeParam) = param.upperBound.exists {
      _.conforms(Singleton(param.projectContext))
    }

    def widenRecursiveInner(`type`: ScType, visited: Set[ParameterizedType]): ScType = `type`.recursiveUpdate {
      case literalType: ScLiteralType => ReplaceWith(literalType.widen)
      // Enum values can be seen as having literal types, SCL-21726
      case ScProjectionType(_, o: ScEnumSingletonCase) =>
        ReplaceWith(if (o.superTypes.length == 1) o.superTypes.head else ScCompoundType(o.superTypes)(`type`.projectContext))
      case parameterizedType@ParameterizedType(oldDesignator@designator.ScDesignatorType(definition: ScTypeDefinition), typeArguments) if !visited(parameterizedType) =>
        val newDesignator = widenRecursiveInner(oldDesignator, visited + parameterizedType)

        val newArgs = definition.typeParameters
          .zip(typeArguments)
          .map {
            case (param, arg) if isSingleton(param) => arg
            case (_, arg) => widenRecursiveInner(arg, visited + parameterizedType)
          }

        ReplaceWith(ScParameterizedType(newDesignator, newArgs))
      case _: ParameterizedType |
           _: ScCompoundType => Stop
      case _ => ProcessSubtypes
    }

    widenRecursiveInner(`type`, Set.empty)
  }
}