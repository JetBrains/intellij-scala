package org.jetbrains.plugins.scala.lang.psi.types

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
        val clazz = result.getElement
        if (clazz != null) {
          return new ScParameterizedType(new ScDesignatorType(clazz), clazz.getTypeParameters.map
                  {tp => create(result.getSubstitutor.substitute(tp), project)})
        }
      }
      case arrayType : PsiArrayType => {
        val arrayClass = JavaPsiFacade.getInstance(project).findClass("scala.Array", GlobalSearchScope.allScope(project))
        if (arrayClass != null) {
          val tps = arrayClass.getTypeParameters
          if (tps.length == 1) {
            val typeArg = create(arrayType.getComponentType, project)
            return new ScParameterizedType(new ScDesignatorType(arrayClass), Array(typeArg))
          }
          return new ScDesignatorType(arrayClass)
        }
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
      case wild : PsiWildcardType => new ScExistentialArgument(create(wild.getSuperBound, project),
                                                               create(wild.getExtendsBound, project))
      case null => new ScExistentialArgument(Nothing, Any) // raw type argument from java 
      case _ => throw new IllegalArgumentException("psi type " + psiType + " should not be converted to scala type")
    }
    Nothing
  }
}