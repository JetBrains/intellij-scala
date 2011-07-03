package org.jetbrains.plugins.scala
package lang
package psi
package types

import impl.ScalaPsiManager
import impl.toplevel.synthetic.ScSyntheticClass
import nonvalue.NonValueType
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import result.{Failure, Success, TypingContext}
import com.intellij.openapi.project.Project
import api.toplevel.typedef.ScObject
import api.statements._

trait ScTypePsiTypeBridge {
  /**
   * @param treatJavaObjectAsAny if true, and paramTopLevel is true, java.lang.Object is treated as scala.Any
   *                             See SCL-3036 and SCL-2375
   */
  def create(psiType: PsiType, project: Project, scope: GlobalSearchScope = null, deep: Int = 0,
             paramTopLevel: Boolean = false, treatJavaObjectAsAny: Boolean = true): ScType = {
    if (deep > 3) // Cranked up from 2 to 3 to solve SCL-2976. But why is this really needed?
      return Any;

    psiType match {
      case classType: PsiClassType => {
        val result = classType.resolveGenerics
        result.getElement match {
          case tp: PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
          case clazz if clazz != null && clazz.getQualifiedName == "java.lang.Object" => {
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
              val containingClass: PsiClass = clazz.getContainingClass
              if (containingClass == null) {
                ScDesignatorType(clazz)
              } else {
                ScProjectionType(constructTypeForClass(containingClass), clazz, ScSubstitutor.empty)
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
      case null => Any //new ScExistentialArgument("_", Nil, Nothing, Any) // raw type argument from java
      case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
    }
  }

  def toPsi(t: ScType, project: Project, scope: GlobalSearchScope): PsiType = {
    if (t.isInstanceOf[NonValueType]) return toPsi(t.inferValueType, project, scope)
    def javaObj = JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName("java.lang.Object", scope)
    t match {
      case Unit => PsiType.VOID
      case Boolean => PsiType.BOOLEAN
      case Char => PsiType.CHAR
      case Int => PsiType.INT
      case Long => PsiType.LONG
      case Float => PsiType.FLOAT
      case Double => PsiType.DOUBLE
      case Byte => PsiType.BYTE
      case Short => PsiType.SHORT
      case Null => PsiType.NULL
      case fun: ScFunctionType => fun.resolveFunctionTrait(project) match {
        case Some(tp) => toPsi(tp, project, scope) case _ => javaObj
      }
      case tuple: ScTupleType => tuple.resolveTupleTrait(project) match {
        case Some(tp) => toPsi(tp, project, scope) case _ => javaObj
      }
      case ScCompoundType(Seq(t, _*), _, _, _) => toPsi(t, project, scope)
      case ScDesignatorType(c: PsiClass) => JavaPsiFacade.getInstance(project).getElementFactory.createType(c, PsiSubstitutor.EMPTY)
      case ScParameterizedType(ScDesignatorType(c: PsiClass), args) =>
        if (c.getQualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
      case ScParameterizedType(proj@ScProjectionType(pr, element, subst), args) => proj.actualElement match {
        case c: PsiClass => if (c.getQualifiedName == "scala.Array" && args.length == 1)
          new PsiArrayType(toPsi(args(0), project, scope))
        else {
          val subst = args.zip(c.getTypeParameters).foldLeft(PsiSubstitutor.EMPTY)
                    {case (s, (targ, tp)) => s.put(tp, toPsi(targ, project, scope))}
          JavaPsiFacade.getInstance(project).getElementFactory.createType(c, subst)
        }
        case _ => javaObj
      }
      case JavaArrayType(arg) => new PsiArrayType(toPsi(arg, project, scope))

      case proj@ScProjectionType(pr, element, subst) => proj.actualElement match {
        case clazz: PsiClass => {
          clazz match {
            case syn: ScSyntheticClass => toPsi(syn.t, project, scope)
            case _ => {
              val fqn = clazz.getQualifiedName
              JavaPsiFacade.getInstance(project).getElementFactory.createTypeByFQClassName(if (fqn != null) fqn else "java.lang.Object", scope)
            }
          }
        }
        case elem: ScTypeAliasDefinition => {
          elem.aliasedType(TypingContext.empty) match {
            case Success(t, _) => toPsi(t, project, scope)
            case Failure(_, _) => javaObj
          }
        }
        case _ => javaObj
      }
      case tpt: ScTypeParameterType =>
        EmptySubstitutor.getInstance().substitute(tpt.param)
      case _ => javaObj
    }
  }
}

