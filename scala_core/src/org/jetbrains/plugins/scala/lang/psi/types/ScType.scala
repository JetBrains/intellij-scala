package org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.psi._
import com.intellij.openapi.project.Project

trait ScType {

  def equiv(t: ScType): Boolean = false

  def conforms(t: ScType): Boolean = false
}

case object Any extends ScType {
  override def equiv(t : ScType) = t == Any
  override def conforms(t: ScType) = true
}

case object Null extends ScType {
  override def equiv(t : ScType) = t == Null
  override def conforms(t: ScType) = t == Null || t == Nothing
}

case object AnyRef extends ScType {
  override def equiv(t : ScType) = t == AnyRef
  override def conforms(t: ScType) = t match {
    case AnyRef => true
    case Null => true
    case _ : ScParameterizedType => true
    case _ : ScSingletonType => true
    case _ => false
  }
}

case object Nothing extends ScType {
  override def equiv(t : ScType) = t == Nothing
  override def conforms(t: ScType) = t == Nothing
}

case object AnyVal extends ScType {
  override def equiv(t : ScType) = t == AnyVal
  override def conforms(t: ScType) = t match {
    case AnyVal => true
    case _ : ValType => true
    case _ => false
  }
}

abstract case class ValType(val name : String, val tSuper : Option[ValType]) extends ScType {
  override def equiv(t : ScType) = t == this
  override def conforms(t : ScType) : Boolean = {
    if (t == this) return true
    tSuper match {
      case Some(tSuper) => conforms(tSuper)
      case _ => false
    }
  }
}

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
  def create(psiType : PsiType, project : Project) : ScType =
    psiType match {
      case classType : PsiClassType => {
        val result = classType.resolveGenerics
        if (result.getElement != null) {
          return new ScParameterizedType(result.getElement, ScSubstitutor.create(result.getSubstitutor, project))
        }
      }
      case arrayType : PsiArrayType => {
        val arrayClass = JavaPsiFacade.getInstance(project).findClass("scala.Array", arrayType.getResolveScope)
        if (arrayClass != null) {
          val tps = arrayClass.getTypeParameters
          var subst = ScSubstitutor.empty
          if (tps.length == 1) {
            subst = subst + (tps(0), create(arrayType.getComponentType, project))
          }
          return new ScParameterizedType(arrayClass, subst)
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
      case _ => Nothing
      //todo other cases
    }
}