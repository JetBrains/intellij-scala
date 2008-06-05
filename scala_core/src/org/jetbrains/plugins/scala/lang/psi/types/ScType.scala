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

case object AnyRef extends ScType {
  override def equiv(t : ScType) = t == AnyRef
  override def conforms(t: ScType) = t match {
    case AnyRef => true
    case _ : ScParameterizedType => true
    case _ : ScSingletonType => true
    case _ => false
  }
}

case object Nothing extends ScType {
  override def equiv(t : ScType) = t == Nothing
  override def conforms(t: ScType) = t == Nothing
}

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
    //todo other cases
    }
    null
  }
}