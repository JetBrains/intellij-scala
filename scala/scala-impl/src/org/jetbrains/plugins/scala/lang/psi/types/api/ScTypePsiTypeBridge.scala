package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.PsiTypeExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

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
    case PsiType.VOID    => Unit
    case PsiType.BOOLEAN => Boolean
    case PsiType.CHAR    => Char
    case PsiType.BYTE    => Byte
    case PsiType.SHORT   => Short
    case PsiType.INT     => Int
    case PsiType.LONG    => Long
    case PsiType.FLOAT   => Float
    case PsiType.DOUBLE  => Double
    case PsiType.NULL    => Null
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
    val stdTypes = std.projectContext.stdTypes
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
      case Unit    => PsiType.VOID
      case Boolean => primitiveOrObject(PsiType.BOOLEAN)
      case Char    => primitiveOrObject(PsiType.CHAR)
      case Byte    => primitiveOrObject(PsiType.BYTE)
      case Short   => primitiveOrObject(PsiType.SHORT)
      case Int     => primitiveOrObject(PsiType.INT)
      case Long    => primitiveOrObject(PsiType.LONG)
      case Float   => primitiveOrObject(PsiType.FLOAT)
      case Double  => primitiveOrObject(PsiType.DOUBLE)
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
