package org.jetbrains.plugins.scala.lang.psi.types

import impl.ScalaPsiManager
import resolve.ScalaResolveResult
import com.intellij.psi._
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

trait ScType {

  def equiv(t: ScType): Boolean = t == this

  sealed def conforms(t: ScType): Boolean = Conformance.conforms(this, t)
}

case object Any extends ScType

case object Null extends ScType

case object AnyRef extends ScType

case object Nothing extends ScType

case object Singleton extends ScType

case object AnyVal extends ScType

abstract case class ValType(val name : String, val tSuper : Option[ValType]) extends ScType

object Unit extends ValType("Unit", None)
object Boolean extends ValType("Boolean", None)
object Char extends ValType("Char", Some(Int))
object Int extends ValType("Int", Some(Long))
object Long extends ValType("Long", Some(Float))
object Float extends ValType("Float", Some(Double))
object Double extends ValType("Double", None)
object Byte extends ValType("Byte", Some(Short))
object Short extends ValType("Float", Some(Int))

object ScType {
  def create(psiType : PsiType, project : Project) : ScType = {
    psiType match {
      case classType : PsiClassType => {
        val result = classType.resolveGenerics
        result.getElement match {
          case tp : PsiTypeParameter => ScalaPsiManager.typeVariable(tp)
          case clazz if clazz != null => {
            val tps = clazz.getTypeParameters
            val des = new ScDesignatorType(clazz)
            tps match {
              case Array() => des
              case _ => new ScParameterizedType(des, tps.map
                    {tp => create(result.getSubstitutor.substitute(tp), project)})
            }
          }
          case _ => Nothing
        }
      }
      case arrayType : PsiArrayType => {
        val arrayClass = JavaPsiFacade.getInstance(project).findClass("scala.Array", GlobalSearchScope.allScope(project))
        if (arrayClass != null) {
          val tps = arrayClass.getTypeParameters
          if (tps.length == 1) {
            val typeArg = create(arrayType.getComponentType, project)
            new ScParameterizedType(new ScDesignatorType(arrayClass), Array(typeArg))
          } else new ScDesignatorType(arrayClass)
        } else Nothing
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
      case wild : PsiWildcardType => new ScExistentialArgument(Nil,
                                                               create(wild.getSuperBound, project),
                                                               create(wild.getExtendsBound, project))
      case null => new ScExistentialArgument(Nil, Nothing, Any) // raw type argument from java 
      case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
    }
  }

  def extractClassType(t : ScType) : Option[Pair[PsiClass, ScSubstitutor]] = t match {
    case ScDesignatorType(clazz : PsiClass) => Some(clazz, ScSubstitutor.empty)
    case proj@ScProjectionType(p, _) => proj.resolveResult match {
      case Some(ScalaResolveResult(c: PsiClass, s)) => {
        val cc = c.getContainingClass
        if (cc != null) Some(c, s.bindOuter(cc, p)) else Some(c, s)
      }
      case None => None
    }
    case p@ScParameterizedType(t1, _) => {
      extractClassType(t1) match {
        case Some((c, s)) => Some((c, s.followed(p.substitutor)))
        case None => None
      }
    }
    case _ => None
  }
}