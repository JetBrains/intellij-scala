package org.jetbrains.plugins.scala
package lang
package psi
package types

import java.util

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypingContext}

import scala.collection.immutable.HashSet

trait ScTypePsiTypeBridge {
  /**
   * @param treatJavaObjectAsAny if true, and paramTopLevel is true, java.lang.Object is treated as scala.Any
   *                             See SCL-3036 and SCL-2375
   */
  def create(psiType: PsiType, project: Project, scope: GlobalSearchScope = null,
             visitedRawTypes: HashSet[PsiClass] = HashSet.empty,
             paramTopLevel: Boolean = false, treatJavaObjectAsAny: Boolean = true): ScType = {
    psiType match {
      case classType: PsiClassType =>
        val result = classType.resolveGenerics
        result.getElement match {
          case tp: PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
          case clazz if clazz != null && clazz.qualifiedName == "java.lang.Object" =>
            if (paramTopLevel && treatJavaObjectAsAny) types.Any
            else types.AnyRef
          case c if c != null =>
            val clazz = c match {
              case o: ScObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(o)
              case _ => c
            }
            if (classType.isRaw && visitedRawTypes.contains(clazz)) return types.Any
            val tps = clazz.getTypeParameters
            def constructTypeForClass(clazz: PsiClass, withTypeParameters: Boolean = false): ScType = {
              clazz match {
                case wrapper: PsiClassWrapper => return constructTypeForClass(wrapper.definition)
                case _ =>
              }
              val containingClass: PsiClass = clazz.containingClass
              val res =
                if (containingClass == null) ScDesignatorType(clazz)
                else {
                  ScProjectionType(constructTypeForClass(containingClass,
                    withTypeParameters = !clazz.hasModifierProperty("static")), clazz, superReference = false)
                }
              if (withTypeParameters) {
                val typeParameters: Array[PsiTypeParameter] = clazz.getTypeParameters
                if (typeParameters.length > 0) {
                  ScParameterizedType(res, typeParameters.map(ptp => new ScTypeParameterType(ptp, ScSubstitutor.empty)))
                } else res
              } else res
            }
            val des = constructTypeForClass(clazz)
            val substitutor = result.getSubstitutor
            tps match {
              case Array() => des
              case _ if classType.isRaw =>
                var index = 0
                ScParameterizedType(des, tps.map({tp => {
                  val arrayOfTypes: Array[PsiClassType] = tp.getExtendsListTypes ++ tp.getImplementsListTypes
                  ScSkolemizedType(s"_$$${index += 1; index}", Nil, types.Nothing,
                    arrayOfTypes.length match {
                      case 0 => types.Any
                      case 1 => create(arrayOfTypes.apply(0), project, scope, visitedRawTypes + clazz)
                      case _ => ScCompoundType(arrayOfTypes.map(create(_, project, scope, visitedRawTypes + clazz)),
                        Map.empty, Map.empty)
                    })
              }})).unpackedType
              case _ =>
                var index = 0
                ScParameterizedType(des, tps.map
                  (tp => {
                    val psiType = substitutor.substitute(tp)
                    psiType match {
                      case wild: PsiWildcardType => ScSkolemizedType(s"_$$${index += 1; index}", Nil,
                        if (wild.isSuper) create(wild.getSuperBound, project, scope, visitedRawTypes) else types.Nothing,
                        if (wild.isExtends) create(wild.getExtendsBound, project, scope, visitedRawTypes) else types.Any)
                      case capture: PsiCapturedWildcardType =>
                        val wild = capture.getWildcard
                        ScSkolemizedType(s"_$$${index += 1; index}", Nil,
                          if (wild.isSuper) create(capture.getLowerBound, project, scope, visitedRawTypes) else types.Nothing,
                          if (wild.isExtends) create(capture.getUpperBound, project, scope, visitedRawTypes) else types.Any)
                      case _ if psiType != null => ScType.create(psiType, project, scope, visitedRawTypes)
                      case _ => ScalaPsiManager.typeVariable(tp)
                    }
                  }).toSeq).unpackedType
            }
          case _ => types.Nothing
        }
      case arrayType: PsiArrayType =>
        JavaArrayType(create(arrayType.getComponentType, project, scope))
      case PsiType.VOID => types.Unit
      case PsiType.BOOLEAN => types.Boolean
      case PsiType.CHAR => types.Char
      case PsiType.INT => types.Int
      case PsiType.LONG => types.Long
      case PsiType.FLOAT => types.Float
      case PsiType.DOUBLE => types.Double
      case PsiType.BYTE => types.Byte
      case PsiType.SHORT => types.Short
      case PsiType.NULL => types.Null
      case wild: PsiWildcardType => ScExistentialType.simpleExistential("_$1", Nil,
        if (wild.isSuper) create(wild.getSuperBound, project, scope, visitedRawTypes) else types.Nothing,
        if (wild.isExtends) create(wild.getExtendsBound, project, scope, visitedRawTypes) else types.Any)
      case capture: PsiCapturedWildcardType =>
        val wild = capture.getWildcard
        ScExistentialType.simpleExistential("_$1", Nil,
          if (wild.isSuper) create(capture.getLowerBound, project, scope, visitedRawTypes) else types.Nothing,
          if (wild.isExtends) create(capture.getUpperBound, project, scope, visitedRawTypes) else types.Any)
      case null => types.Any
      case d: PsiDisjunctionType => types.Any
      case d: PsiDiamondType =>
        val tps: util.List[PsiType] = d.resolveInferredTypes().getInferredTypes
        if (tps.size() > 0) {
          create(tps.get(0), project, scope, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)
        } else {
          if (paramTopLevel && treatJavaObjectAsAny) types.Any
          else types.AnyRef
        }
      case p: PsiIntersectionType =>
        ScCompoundType(p.getConjuncts.map(create(_, project, scope, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)),
          Map.empty, Map.empty)
      case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
    }
  }

  def toPsi(_t: ScType, project: Project, scope: GlobalSearchScope, noPrimitives: Boolean = false,
            skolemToWildcard: Boolean = false): PsiType = {
    def isValueType(cl: ScClass): Boolean = cl.superTypes.contains(AnyVal) && cl.parameters.length == 1

    def outerClassHasTypeParameters(proj: ScProjectionType): Boolean = {
      ScType.extractClass(proj.projected) match {
        case Some(outer) => outer.hasTypeParameters
        case _ => false
      }
    }

    def createType(c: PsiClass, subst: PsiSubstitutor = PsiSubstitutor.EMPTY, raw: Boolean = false): PsiType = {
      val psiType = JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
      if (raw) psiType.rawType()
      else psiType
    }

    def createTypeByFqn(fqn: String): PsiType = {
      JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName(fqn, scope)
    }

    val t = ScType.removeAliasDefinitions(_t)
    if (t.isInstanceOf[NonValueType]) return toPsi(t.inferValueType, project, scope)
    def javaObj = createTypeByFqn("java.lang.Object")
    t match {
      case types.Any => javaObj
      case types.AnyRef => javaObj
      case types.Unit =>
        if (noPrimitives) {
          val boxed = createTypeByFqn("scala.runtime.BoxedUnit")
          if (boxed != null) boxed
          else javaObj
        } else PsiType.VOID
      case types.Boolean => if (noPrimitives) javaObj else PsiType.BOOLEAN
      case types.Char => if (noPrimitives) javaObj else PsiType.CHAR
      case types.Int => if (noPrimitives) javaObj else PsiType.INT
      case types.Long => if (noPrimitives) javaObj else PsiType.LONG
      case types.Float => if (noPrimitives) javaObj else PsiType.FLOAT
      case types.Double => if (noPrimitives) javaObj else PsiType.DOUBLE
      case types.Byte => if (noPrimitives) javaObj else PsiType.BYTE
      case types.Short => if (noPrimitives) javaObj else PsiType.SHORT
      case types.Null => javaObj
      case types.Nothing => javaObj
      case ScCompoundType(Seq(typez, _*), _, _) => toPsi(typez, project, scope)
      case ScDesignatorType(c: ScTypeDefinition) if StdType.QualNameToType.contains(c.qualifiedName) =>
        toPsi(StdType.QualNameToType.get(c.qualifiedName).get, project, scope, noPrimitives, skolemToWildcard)
      case ScDesignatorType(valType: ScClass) if isValueType(valType) =>
        valType.parameters.head.getRealParameterType(TypingContext.empty) match {
          case Success(tp, _) if !(noPrimitives && ScalaEvaluatorBuilderUtil.isPrimitiveScType(tp)) =>
            toPsi(tp, project, scope, noPrimitives, skolemToWildcard)
          case _ => createType(valType)
        }
      case ScDesignatorType(c: PsiClass) => createType(c)
      case ScParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        if (c.qualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args.head, project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY) {
            case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope, noPrimitives = true, skolemToWildcard = true))
          }
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
      case ScParameterizedType(proj@ScProjectionType(pr, element, _), args) => proj.actualElement match {
        case c: PsiClass =>
          if (c.qualifiedName == "scala.Array" && args.length == 1) new PsiArrayType(toPsi(args.head, project, scope))
          else {
            val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
            {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope, skolemToWildcard = true))}
            createType(c, subst, raw = outerClassHasTypeParameters(proj))
          }
        case a: ScTypeAliasDefinition =>
          a.aliasedType(TypingContext.empty) match {
            case Success(c: ScParameterizedType, _) =>
              toPsi(ScParameterizedType(c.designator, args), project, scope, noPrimitives)
            case _ => javaObj
          }
        case _ => javaObj
      }
      case ScParameterizedType(tpt: ScTypeParameterType, _) => EmptySubstitutor.getInstance().substitute(tpt.param)
      case JavaArrayType(arg) => new PsiArrayType(toPsi(arg, project, scope))
      case proj@ScProjectionType(_, _, _) => proj.actualElement match {
        case clazz: PsiClass =>
          clazz match {
            case syn: ScSyntheticClass => toPsi(syn.t, project, scope)
            case _ => createType(clazz, raw = outerClassHasTypeParameters(proj))
          }
        case elem: ScTypeAliasDefinition =>
          elem.aliasedType(TypingContext.empty) match {
            case Success(typez, _) => toPsi(typez, project, scope, noPrimitives)
            case Failure(_, _) => javaObj
          }
        case _ => javaObj
      }
      case ScThisType(clazz) => createType(clazz)
      case tpt: ScTypeParameterType => EmptySubstitutor.getInstance().substitute(tpt.param)
      case ex: ScExistentialType => toPsi(ex.skolem, project, scope, noPrimitives)
      case argument: ScSkolemizedType =>
        val upper = argument.upper
        if (upper.equiv(types.Any)) {
          val lower = argument.lower
          if (lower.equiv(types.Nothing)) PsiWildcardType.createUnbounded(PsiManager.getInstance(project))
          else {
            val sup: PsiType = toPsi(lower, project, scope)
            if (sup.isInstanceOf[PsiWildcardType]) javaObj
            else PsiWildcardType.createSuper(PsiManager.getInstance(project), sup)
          }
        } else {
          val psi = toPsi(upper, project, scope)
          if (psi.isInstanceOf[PsiWildcardType]) javaObj
          else PsiWildcardType.createExtends(PsiManager.getInstance(project), psi)
        }
      case _ => javaObj
    }
  }
}

