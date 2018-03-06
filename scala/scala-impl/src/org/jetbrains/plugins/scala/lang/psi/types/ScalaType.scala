package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType



/**
  * @author adkozlov
  */
trait ScalaType extends ScType {
  override def typeSystem: ScalaTypeSystem = ScalaTypeSystem
}

object ScalaType {
  /**
    * Expands type aliases, including those in a type projection. Type Alias Declarations are replaced by their upper
    * bound.
    *
    * @see http://youtrack.jetbrains.net/issue/SCL-2872
    */
  // TODO This is all a bit ad-hoc. What can we learn from scalac?
  // TODO perhaps we need to choose the lower bound if we are in a contravariant position. We get away
  //      with this as we currently only rely on this method to determine covariant types: the parameter
  //      types of FunctionN, or the elements of TupleN
  def expandAliases(tp: ScType, visited: Set[ScType] = Set.empty): TypeResult = {

    if (visited contains tp) return Right(tp)
    tp match {
      case proj@ScProjectionType(_, _) => proj.actualElement match {
        case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
          t.aliasedType.flatMap(t => expandAliases(proj.actualSubst.subst(t), visited + tp))
        case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty =>
          t.upperBound.flatMap(upper => expandAliases(proj.actualSubst.subst(upper), visited + tp))
        case _ => Right(tp)
      }
      case at: ScAbstractType => expandAliases(at.upper, visited + tp) // ugly hack for SCL-3592
      case ScDesignatorType(t: ScType) => expandAliases(t, visited + tp)
      case ScDesignatorType(ta: ScTypeAliasDefinition) => expandAliases(ta.aliasedType.getOrNothing, visited + tp)
      case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty =>
        t.upperBound.flatMap(expandAliases(_, visited + tp))
      case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
        t.aliasedType
      case pt: ScParameterizedType if pt.isAliasType.isDefined =>
        val aliasType: AliasType = pt.isAliasType.get
        aliasType.upper.flatMap(expandAliases(_, visited + tp))
      case _ => Right(tp)
    }
  }

  /**
    * Creates a type that designates `element`. Usually this will be a ScDesignatorType, except for the
    * special case when `element` represent a standard type, such as scala.Double.
    *
    * @see http://youtrack.jetbrains.net/issue/SCL-2913
    */
  def designator(element: PsiNamedElement): ScType = {
    element match {
      case clazz: ScClass if !Option(clazz.getContext).exists(c => c.isInstanceOf[ScTemplateBody] || c.isInstanceOf[ScEarlyDefinitions]) =>
        val designatorType = ScDesignatorType(element)
        designatorType.getValType.getOrElse(designatorType)
      case _ =>
        val clazzOpt = element match {
          case p: ScClassParameter => Option(p.containingClass)
          case _ => element.getContext match {
            case _: ScTemplateBody | _: ScEarlyDefinitions =>
              Option(ScalaPsiUtil.contextOfType(element, strict = true, classOf[ScTemplateDefinition]))
            case _ => None
          }
        }

        clazzOpt match {
          case Some(clazz) => ScProjectionType(ScThisType(clazz), element)
          case _ => ScDesignatorType(element)
        }
    }
  }
}
