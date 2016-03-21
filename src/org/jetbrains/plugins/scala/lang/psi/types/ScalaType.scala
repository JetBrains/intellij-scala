package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */
trait ScalaType extends ScType {
  implicit val typeSystem = ScalaTypeSystem

  val isSingleton: Boolean = false
}

object ScalaType extends ScTypePresentation {
  override implicit val typeSystem = ScalaTypeSystem

  /**
    * Returns named element associated with type `t`.
    * If withoutAliases is `true` expands alias definitions first
    *
    * @param t              type
    * @param withoutAliases need to expand alias or not
    * @return element and substitutor
    */
  def extractDesignated(t: ScType, withoutAliases: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = t match {
    case n: NonValueType => extractDesignated(n.inferValueType, withoutAliases)
    case ScDesignatorType(ta: ScTypeAliasDefinition) if withoutAliases =>
      val result = ta.aliasedType(TypingContext.empty)
      if (result.isEmpty) return None
      extractDesignated(result.get, withoutAliases)
    case ScDesignatorType(e) => Some(e, ScSubstitutor.empty)
    case ScThisType(c) => Some(c, ScSubstitutor.empty)
    case proj@ScProjectionType(p, e, _) => proj.actualElement match {
      case t: ScTypeAliasDefinition if withoutAliases =>
        val result = t.aliasedType(TypingContext.empty)
        if (result.isEmpty) return None
        extractDesignated(proj.actualSubst.subst(result.get), withoutAliases)
      case _ => Some((proj.actualElement, proj.actualSubst))
    }
    case p@ScParameterizedType(t1, _) =>
      extractDesignated(t1, withoutAliases) match {
        case Some((e, s)) => Some((e, s.followed(p.substitutor)))
        case None => None
      }
    case std@StdType(_, _) =>
      val asClass = std.asClass(DecompilerUtil.obtainProject)
      if (asClass.isEmpty) return None
      Some((asClass.get, ScSubstitutor.empty))
    case ScTypeParameterType(_, _, _, _, param) => Some(param, ScSubstitutor.empty)
    case _ => None
  }

  def extractDesignatorSingletonType(tp: ScType): Option[ScType] = tp match {
    case ScDesignatorType(v) =>
      v match {
        case o: ScObject => None
        case p: ScParameter if p.isStable => p.getRealParameterType(TypingContext.empty).toOption
        case t: ScTypedDefinition if t.isStable => t.getType(TypingContext.empty).toOption
        case _ => None
      }
    case proj@ScProjectionType(_, elem, _) =>
      elem match {
        case o: ScObject => None
        case p: ScParameter if p.isStable => p.getRealParameterType(TypingContext.empty).toOption.map(proj.actualSubst.subst)
        case t: ScTypedDefinition if t.isStable => t.getType(TypingContext.empty).toOption.map(proj.actualSubst.subst)
        case _ => None
      }
    case _ => None
  }

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
  def expandAliases(tp: ScType, visited: HashSet[ScType] = HashSet.empty): TypeResult[ScType] = {
    if (visited contains tp) return Success(tp, None)
    tp match {
      case proj@ScProjectionType(p, elem, _) => proj.actualElement match {
        case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
          t.aliasedType(TypingContext.empty).flatMap(t => expandAliases(proj.actualSubst.subst(t), visited + tp))
        case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty =>
          t.upperBound.flatMap(upper => expandAliases(proj.actualSubst.subst(upper), visited + tp))
        case _ => Success(tp, None)
      }
      case at: ScAbstractType => expandAliases(at.upper, visited + tp) // ugly hack for SCL-3592
      case ScDesignatorType(t: ScType) => expandAliases(t, visited + tp)
      case ScDesignatorType(ta: ScTypeAliasDefinition) => expandAliases(ta.aliasedType(TypingContext.empty).getOrNothing, visited + tp)
      case t: ScTypeAliasDeclaration if t.typeParameters.isEmpty =>
        t.upperBound.flatMap(expandAliases(_, visited + tp))
      case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
        t.aliasedType(TypingContext.empty)
      case pt: ScParameterizedType if pt.isAliasType.isDefined =>
        val aliasType: AliasType = pt.isAliasType.get
        aliasType.upper.flatMap(expandAliases(_, visited + tp))
      case _ => Success(tp, None)
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
      case td: ScClass => StdType.QualNameToType.getOrElse(td.qualifiedName, new ScDesignatorType(element))
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
          case Some(clazz) => ScProjectionType(ScThisType(clazz), element, superReference = false)
          case _ => new ScDesignatorType(element)
        }
    }
  }
}