package org.jetbrains.plugins.scala
package lang
package psi
package types

import impl.ScalaPsiManager
import impl.toplevel.synthetic.ScSyntheticClass
import nonvalue.NonValueType
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import impl.source.PsiImmediateClassType
import result.{Failure, Success, TypingContext}
import com.intellij.openapi.project.Project
import api.statements._
import light.PsiClassWrapper
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClassType.ClassResolveResult
import api.toplevel.typedef.{ScTypeDefinition, ScObject}
import extensions.{toPsiMemberExt, toPsiClassExt}

trait ScTypePsiTypeBridge {
  /**
   * @param treatJavaObjectAsAny if true, and paramTopLevel is true, java.lang.Object is treated as scala.Any
   *                             See SCL-3036 and SCL-2375
   */
  def create(psiType: PsiType, project: Project, scope: GlobalSearchScope = null, deep: Int = 0,
             paramTopLevel: Boolean = false, treatJavaObjectAsAny: Boolean = true): ScType = {
    if (deep > 3) // Cranked up from 2 to 3 to solve SCL-2976. But why is this really needed?
      return Any

    psiType match {
      case classType: PsiClassType => {
        val result = classType.resolveGenerics
        result.getElement match {
          case tp: PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
          case clazz if clazz != null && clazz.qualifiedName == "java.lang.Object" => {
            if (paramTopLevel && treatJavaObjectAsAny) Any
            else AnyRef
          }
          case c if c != null => {
            val clazz = c match {
              case o: ScObject => ScalaPsiUtil.getCompanionModule(o).getOrElse(o)
              case _ => c
            }
            val tps = clazz.getTypeParameters
            def constructTypeForClass(clazz: PsiClass): ScType = {
              clazz match {
                case wrapper: PsiClassWrapper => return constructTypeForClass(wrapper.definition)
                case _ =>
              }
              val containingClass: PsiClass = clazz.containingClass
              if (containingClass == null) {
                ScDesignatorType(clazz)
              } else {
                ScProjectionType(constructTypeForClass(containingClass), clazz, ScSubstitutor.empty, false)
              }
            }
            val des = constructTypeForClass(clazz)
            val substitutor = result.getSubstitutor
            tps match {
              case Array() => des
              case _ if classType.isRaw => {
                new ScParameterizedType(des, collection.immutable.Seq(tps.map({tp => {
                  val arrayOfTypes: Array[PsiClassType] = tp.getExtendsListTypes ++ tp.getImplementsListTypes
                  new ScExistentialArgument("_", Nil, Nothing,
                    arrayOfTypes.length match {
                      case 0 => Any
                      case 1 => create(arrayOfTypes.apply(0), project, scope, deep + 1)
                      case _ => ScCompoundType(arrayOfTypes.map(create(_, project, scope, deep + 1)), Seq.empty, Seq.empty, ScSubstitutor.empty)
                    })
              }}): _*))
              }
              case _ => new ScParameterizedType(des, collection.immutable.Seq(tps.map
                        (tp => {
                val psiType = substitutor.substitute(tp)
                if (psiType != null) ScType.create(psiType, project, scope, deep + 1)
                else ScalaPsiManager.typeVariable(tp)
              }).toSeq: _*))
            }
          }
          case _ => Nothing
        }
      }
      case arrayType: PsiArrayType => {
        JavaArrayType(create(arrayType.getComponentType, project, scope))
      }

      case PsiType.VOID => Unit
      case PsiType.BOOLEAN => Boolean
      case PsiType.CHAR => Char
      case PsiType.INT => Int
      case PsiType.LONG => Long
      case PsiType.FLOAT => Float
      case PsiType.DOUBLE => Double
      case PsiType.BYTE => Byte
      case PsiType.SHORT => Short
      case PsiType.NULL => Null
      case wild: PsiWildcardType => new ScExistentialArgument("_", Nil,
        if (wild.isSuper) create(wild.getSuperBound, project, scope, deep + 1) else Nothing,
        if (wild.isExtends) create(wild.getExtendsBound, project, scope, deep + 1) else Any)
      case capture: PsiCapturedWildcardType =>
        val wild = capture.getWildcard
        new ScSkolemizedType("_", Nil,
          if (wild.isSuper) create(capture.getLowerBound, project, scope) else Nothing,
          if (wild.isExtends) create(capture.getUpperBound, project, scope) else Any)
      case null => Any
      case d: PsiDisjunctionType => Any
      case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
    }
  }

  def toPsi(t: ScType, project: Project, scope: GlobalSearchScope, noPrimitives: Boolean = false): PsiType = {
    if (t.isInstanceOf[NonValueType]) return toPsi(t.inferValueType, project, scope)
    def javaObj = JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName("java.lang.Object", scope)
    t match {
      case Any => javaObj
      case AnyRef => javaObj
      case Unit => if (noPrimitives) javaObj else PsiType.VOID
      case Boolean => if (noPrimitives) javaObj else PsiType.BOOLEAN
      case Char => if (noPrimitives) javaObj else PsiType.CHAR
      case Int => if (noPrimitives) javaObj else PsiType.INT
      case Long => if (noPrimitives) javaObj else PsiType.LONG
      case Float => if (noPrimitives) javaObj else PsiType.FLOAT
      case Double => if (noPrimitives) javaObj else PsiType.DOUBLE
      case Byte => if (noPrimitives) javaObj else PsiType.BYTE
      case Short => if (noPrimitives) javaObj else PsiType.SHORT
      case Null => javaObj
      case Nothing => javaObj
      case fun: ScFunctionType => fun.resolveFunctionTrait(project) match {
        case Some(tp) => toPsi(tp, project, scope) case _ => javaObj
      }
      case tuple: ScTupleType => tuple.resolveTupleTrait(project) match {
        case Some(tp) => toPsi(tp, project, scope) case _ => javaObj
      }
      case ScCompoundType(Seq(t, _*), _, _, _) => toPsi(t, project, scope)
      case ScDesignatorType(c: PsiClass) => JavaPsiFacade.getInstance(project).getElementFactory.createType(c, PsiSubstitutor.EMPTY)
      case ScParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        if (c.qualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope, true))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
      case ScParameterizedType(proj@ScProjectionType(pr, element, subst, _), args) => proj.actualElement match {
        case c: PsiClass => if (c.qualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
        case a: ScTypeAliasDefinition =>
          a.aliasedType(TypingContext.empty) match {
            case Success(c: ScParameterizedType, _) =>
              toPsi(c.copy(typeArgs = args), project, scope, noPrimitives)
            case _ => javaObj
          }
        case _ => javaObj
      }
      case JavaArrayType(arg) => new PsiArrayType(toPsi(arg, project, scope))

      case proj@ScProjectionType(pr, element, subst, _) => proj.actualElement match {
        case clazz: PsiClass => {
          clazz match {
            case syn: ScSyntheticClass => toPsi(syn.t, project, scope)
            case _ => {
              val fqn = clazz.qualifiedName
              JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName(if (fqn != null) fqn else "java.lang.Object", scope)
            }
          }
        }
        case elem: ScTypeAliasDefinition => {
          elem.aliasedType(TypingContext.empty) match {
            case Success(t, _) => toPsi(t, project, scope, noPrimitives)
            case Failure(_, _) => javaObj
          }
        }
        case _ => javaObj
      }
      case ScThisType(clazz) => JavaPsiFacade.getInstance(project).getElementFactory.createType(clazz, PsiSubstitutor.EMPTY)
      case tpt: ScTypeParameterType =>
        EmptySubstitutor.getInstance().substitute(tpt.param)
      case argument: ScExistentialArgument =>
        val upper = argument.upperBound
        if (upper.equiv(Any)) {
          val lower = argument.lowerBound
          if (lower.equiv(Nothing)) PsiWildcardType.createUnbounded(PsiManager.getInstance(project))
          else {
            PsiWildcardType.createSuper(PsiManager.getInstance(project), toPsi(lower, project, scope))
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

