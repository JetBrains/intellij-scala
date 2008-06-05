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
  override def conforms(t: ScType) = t == Null
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

object UnitType extends ValType("Unit", None)
object BooleanType extends ValType("Boolean", None)
object CharType extends ValType("Char", Some(IntType))
object IntType extends ValType("Int", Some(LongType))
object LongType extends ValType("Long", Some(FloatType))
object FloatType extends ValType("Float", Some(DoubleType))
object DoubleType extends ValType("Double", None)
object ByteType extends ValType("Byte", Some(ShortType))
object ShortType extends ValType("Float", Some(IntType))

object ScType {
  def create(psiType : PsiType, project : Project) : ScType = {
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
          val subst = ScSubstitutor.empty + (tps(0), create(arrayType.getComponentType, project))
          return new ScParameterizedType(arrayClass, subst)
        }
      }

      case PsiType.VOID => UnitType
      case PsiType.BOOLEAN => BooleanType
      case PsiType.CHAR => CharType
      case PsiType.INT => IntType
      case PsiType.LONG => LongType
      case PsiType.FLOAT => FloatType
      case PsiType.DOUBLE => DoubleType
      case PsiType.BYTE => ByteType
      case PsiType.SHORT => ShortType
      case PsiType.NULL => Null
    //todo other cases
    }
    null
  }
}