package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.project.ProjectExt

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
               treatJavaObjectAsAny: Boolean)
              (implicit visitedRawTypes: HashSet[PsiClass],
               paramTopLevel: Boolean): ScType = `type` match {
    case arrayType: PsiArrayType =>
      JavaArrayType(arrayType.getComponentType.toScType())
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
      import scala.collection.JavaConversions._
      diamondType.resolveInferredTypes().getInferredTypes.toList map {
        toScType(_, treatJavaObjectAsAny)
      } match {
        case Nil if paramTopLevel && treatJavaObjectAsAny => Any
        case Nil => AnyRef
        case head :: _ => head
      }
    case wildcardType: PsiCapturedWildcardType =>
      toScType(wildcardType.getWildcard, treatJavaObjectAsAny)
    case intersectionType: PsiIntersectionType =>
      typeSystem.andType(intersectionType.getConjuncts.map {
        toScType(_, treatJavaObjectAsAny)
      })
    case _ => throw new IllegalArgumentException(s"psi type ${`type`} should not be converted to ${typeSystem.name} type")
  }

  def toPsiType(`type`: ScType,
                noPrimitives: Boolean = false,
                skolemToWildcard: Boolean = false)
               (implicit elementScope: ElementScope): PsiType = {
    def javaObject = createJavaObject
    def primitiveOrObject(primitive: PsiPrimitiveType) =
      if (noPrimitives) javaObject else primitive

    `type` match {
      case Any => javaObject
      case AnyRef => javaObject
      case Unit if noPrimitives =>
        Option(createTypeByFqn("scala.runtime.BoxedUnit"))
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
      case JavaArrayType(arg) => new PsiArrayType(toPsiType(arg))
      case _ => javaObject
    }
  }

  def extractClass(`type`: ScType,
                   project: Project = null): Option[PsiClass] =
    extractClassType(`type`, project).map(_._1)

  def extractClassType(`type`: ScType,
                       project: Project = null,
                       visitedAlias: HashSet[ScTypeAlias] = HashSet.empty): Option[(PsiClass, ScSubstitutor)] =
    `type` match {
      case nonValueType: NonValueType =>
        nonValueType.inferValueType.extractClassType(project, visitedAlias)
      case designatorOwner: DesignatorOwner =>
        designatorOwner.classType(project, visitedAlias)
      case parameterizedType: ParameterizedType =>
        parameterizedType.designator.extractClassType(project, visitedAlias).map {
          case (clazz, substitutor) => (clazz, substitutor.followed(parameterizedType.substitutor))
        }
      case stdType: StdType =>
        stdType.asClass(Option(project).getOrElse(DecompilerUtil.obtainProject))
          .map {
            (_, ScSubstitutor.empty)
          }
      case _ => None
    }

  protected def createType(psiClass: PsiClass,
                           substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
                           raw: Boolean = false)
                          (implicit elementScope: ElementScope): PsiType = {
    val psiType = factory.createType(psiClass, substitutor)
    if (raw) psiType.rawType
    else psiType
  }

  protected def createJavaObject(implicit elementScope: ElementScope): PsiType =
    createTypeByFqn("java.lang.Object")

  private def createTypeByFqn(fqn: String)
                             (implicit elementScope: ElementScope): PsiType =
    factory.createTypeByFQClassName(fqn, elementScope.scope)

  protected def factory(implicit elementScope: ElementScope): PsiElementFactory =
    JavaPsiFacade.getInstance(elementScope.project).getElementFactory

}

object ExtractClass {
  def unapply(`type`: ScType)(implicit typeSystem: TypeSystem): Option[PsiClass] = {
    `type`.extractClass()
  }

  def unapply(`type`: ScType, project: Project): Option[PsiClass] = {
    unapply(`type`)(project.typeSystem)
  }
}

object ScalaArrayOf {
  def unapply(scType: ScType): Option[ScType] = scType match {
    case ParameterizedType(ScDesignatorType(cl: ScClass), Seq(arg))
      if cl.qualifiedName == "scala.Array" => Some(arg)
    case _ => None
  }
}