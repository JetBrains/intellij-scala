package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.types._

import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */
trait ScTypePsiTypeBridge extends TypeSystemOwner {
  /**
    * @param treatJavaObjectAsAny if true, and paramTopLevel is true, java.lang.Object is treated as scala.Any
    *                             See SCL-3036 and SCL-2375
    */
  def toScType(`type`: PsiType,
               project: Project,
               scope: GlobalSearchScope = null,
               visitedRawTypes: HashSet[PsiClass] = HashSet.empty,
               paramTopLevel: Boolean = false,
               treatJavaObjectAsAny: Boolean = true): ScType = `type` match {
    case arrayType: PsiArrayType => JavaArrayType(toScType(arrayType.getComponentType, project, scope))
    case PsiType.VOID => Unit
    case PsiType.BOOLEAN => Boolean
    case PsiType.CHAR => Char
    case PsiType.BYTE => Byte
    case PsiType.SHORT => Short
    case PsiType.INT => Int
    case PsiType.LONG => Long
    case PsiType.FLOAT => Float
    case PsiType.DOUBLE => Double
    case PsiType.NULL => Null
    case null => Any
    case diamondType: PsiDiamondType =>
      val types = diamondType.resolveInferredTypes().getInferredTypes
      if (types.isEmpty) {
        if (paramTopLevel && treatJavaObjectAsAny) Any else AnyRef
      } else {
        toScType(types.get(0), project, scope, visitedRawTypes, paramTopLevel, treatJavaObjectAsAny)
      }
    case _ => throw new IllegalArgumentException(s"psi type ${`type`} should not be converted to ${typeSystem.name} type")
  }

  def toPsiType(`type`: ScType,
                project: Project,
                scope: GlobalSearchScope,
                noPrimitives: Boolean = false,
                skolemToWildcard: Boolean = false): PsiType = {
    def javaObject = createJavaObject(project, scope)
    def primitiveOrObject(primitive: PsiPrimitiveType) =
      if (noPrimitives) javaObject else primitive

    `type` match {
      case Any => javaObject
      case AnyRef => javaObject
      case Unit if noPrimitives =>
        Option(createTypeByFqn(project, scope, "scala.runtime.BoxedUnit"))
          .getOrElse(javaObject)
      case Unit => PsiType.VOID
      case Boolean => primitiveOrObject(PsiType.BOOLEAN)
      case Char => primitiveOrObject(PsiType.CHAR)
      case Byte => primitiveOrObject(PsiType.BYTE)
      case Short => primitiveOrObject(PsiType.SHORT)
      case Int => primitiveOrObject(PsiType.INT)
      case Long => primitiveOrObject(PsiType.LONG)
      case Float => primitiveOrObject(PsiType.FLOAT)
      case Double => primitiveOrObject(PsiType.DOUBLE)
      case Null => javaObject
      case Nothing => javaObject
      case JavaArrayType(arg) => new PsiArrayType(toPsiType(arg, project, scope))
      case _ => javaObject
    }
  }

  protected def createType(psiClass: PsiClass,
                           project: Project,
                           substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
                           raw: Boolean = false): PsiType = {
    val psiType = factory(project).createType(psiClass, substitutor)
    if (raw) psiType.rawType
    else psiType
  }

  protected def createJavaObject(project: Project, scope: GlobalSearchScope) = {
    createTypeByFqn(project, scope, "java.lang.Object")
  }

  private def createTypeByFqn(project: Project, scope: GlobalSearchScope, fqn: String): PsiType = {
    factory(project).createTypeByFQClassName(fqn, scope)
  }

  private def factory(project: Project) =
    JavaPsiFacade.getInstance(project).getElementFactory
}
