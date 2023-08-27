package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

trait PsiTypeBridge {
  typeSystem: TypeSystem =>

  /**
    * @param treatJavaObjectAsAny if true, and paramTopLevel is true, java.lang.Object is treated as scala.Any
    *                             See SCL-3036 and SCL-2375
    */
  def toScType(@Nullable psiType: PsiType, treatJavaObjectAsAny: Boolean, paramTopLevel: Boolean): ScType =
    toScTypeInner(psiType, treatJavaObjectAsAny, paramTopLevel, rawExistentialArguments = None)

  protected type RawExistentialArgs = Map[PsiTypeParameter, ScExistentialArgument]

  //Result of this method may contain not-bound existential arguments,
  //because we need to defer initialization of wildcard if it is called recursively.
  protected def toScTypeInner(@Nullable psiType: PsiType,
                              paramTopLevel: Boolean = false,
                              treatJavaObjectAsAny: Boolean = true,
                              rawExistentialArguments: Option[RawExistentialArgs] = None): ScType = psiType match {
    case arrayType: PsiArrayType =>
      JavaArrayType(arrayType.getComponentType.toScType())
    case PsiTypeConstants.Void    => Unit
    case PsiTypeConstants.Boolean => Boolean
    case PsiTypeConstants.Char    => Char
    case PsiTypeConstants.Byte    => Byte
    case PsiTypeConstants.Short   => Short
    case PsiTypeConstants.Int     => Int
    case PsiTypeConstants.Long    => Long
    case PsiTypeConstants.Float   => Float
    case PsiTypeConstants.Double  => Double
    case PsiTypeConstants.Null    => Null
    case null            => Any
    case diamondType: PsiDiamondType =>
      diamondType.resolveInferredTypes().getInferredTypes.asScala.toList.map {
        toScTypeInner(_, paramTopLevel, treatJavaObjectAsAny, rawExistentialArguments)
      } match {
        case Nil if paramTopLevel && treatJavaObjectAsAny => Any
        case Nil => AnyRef
        case head :: _ => head
      }
    case wildcardType: PsiCapturedWildcardType =>
      toScTypeInner(wildcardType.getWildcard, paramTopLevel, treatJavaObjectAsAny, rawExistentialArguments)
    case intersectionType: PsiIntersectionType =>
      typeSystem.andType(ArraySeq.unsafeWrapArray(intersectionType.getConjuncts.map {
        toScTypeInner(_, paramTopLevel, treatJavaObjectAsAny, rawExistentialArguments)
      }))
    case _ => throw new IllegalArgumentException(s"psi type $psiType should not be converted to ${typeSystem.name} type")
  }

  def toPsiType(`type`: ScType, noPrimitives: Boolean = false): PsiType

  final def stdToPsiType(std: StdType, noPrimitives: Boolean = false): PsiType = {
    val stdTypes = std.getProject.stdTypes
    import stdTypes._

    def javaObject = createJavaObject

    def primitiveOrObject(primitive: PsiPrimitiveType) =
      if (noPrimitives) javaObject else primitive

    std match {
      case Any    => javaObject
      case AnyRef => javaObject
      case Unit if noPrimitives =>
        Option(createTypeByFqn("scala.runtime.BoxedUnit"))
          .getOrElse(javaObject)
      case Unit    => PsiTypeConstants.Void
      case Boolean => primitiveOrObject(PsiTypeConstants.Boolean)
      case Char    => primitiveOrObject(PsiTypeConstants.Char)
      case Byte    => primitiveOrObject(PsiTypeConstants.Byte)
      case Short   => primitiveOrObject(PsiTypeConstants.Short)
      case Int     => primitiveOrObject(PsiTypeConstants.Int)
      case Long    => primitiveOrObject(PsiTypeConstants.Long)
      case Float   => primitiveOrObject(PsiTypeConstants.Float)
      case Double  => primitiveOrObject(PsiTypeConstants.Double)
      case Null    => javaObject
      case Nothing => javaObject
      case _       => javaObject
    }
  }

  protected def createType(psiClass: PsiClass,
                           substitutor: PsiSubstitutor = PsiSubstitutor.EMPTY,
                           raw: Boolean = false): PsiType = {
    val psiType = factory.createType(psiClass, substitutor)
    if (raw) psiType.rawType
    else psiType
  }

  protected def createJavaObject: PsiType =
    createTypeByFqn("java.lang.Object")

  private def createTypeByFqn(fqn: String): PsiType =
    factory.createTypeByFQClassName(fqn, GlobalSearchScope.allScope(projectContext))

  protected def factory: PsiElementFactory =
    JavaPsiFacade.getInstance(projectContext).getElementFactory

}

object ExtractClass {
  def unapply(`type`: ScType): Option[PsiClass] =
    `type`.extractClass
}

object arrayType {
  def unapply(scType: ScType): Option[ScType] = scType match {
    case ParameterizedType(ScDesignatorType(cl: ScClass), Seq(arg))
      if cl.qualifiedName == "scala.Array" => Some(arg)
    case JavaArrayType(arg) => Some(arg)
    case _ => None
  }
}
