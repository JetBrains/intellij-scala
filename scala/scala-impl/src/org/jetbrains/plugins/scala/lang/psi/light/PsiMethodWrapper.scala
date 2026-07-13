package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import com.intellij.psi.impl.PsiSuperMethodImplUtil
import com.intellij.psi.impl.light._
import com.intellij.psi.util.{MethodSignature, MethodSignatureBackedByPsiMethod}
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.util.HashBuilder._

import java.util

abstract class PsiMethodWrapper[T <: ScalaPsiElement with PsiNamedElement with NavigatablePsiElement](
  override val delegate: T,
  methodName: String,
  containingClass: PsiClass
) extends LightMethodBuilder(delegate.getManager, containingClass.getLanguage, methodName)
  with NavigablePsiElementWrapper[T] {

  implicit def elementScope: ElementScope = ElementScope(containingClass)

  setContainingClass(containingClass)

  @volatile private var _returnType: PsiType = NullPsiType

  @volatile private var _typeParameterList: PsiTypeParameterList = _

  @volatile private var _modifierList: PsiModifierList = _

  @volatile private var _parameterList: PsiParameterList = _

  @volatile private var _throwsList: PsiReferenceList = _

  protected def returnScType: ScType

  protected def parameters: Seq[PsiParameter]

  protected def typeParameters: Seq[PsiTypeParameter]

  protected def modifierList: PsiModifierList

  override def getThrowsList: PsiReferenceList = {
    if (_throwsList == null) {
      _throwsList = ScLightThrowsList(delegate)
    }
    _throwsList
  }

  override def getModifierList: PsiModifierList = {
    if (_modifierList == null) {
      _modifierList = modifierList
    }
    _modifierList
  }

  override def getTypeParameterList: PsiTypeParameterList = {
    if (_typeParameterList == null) {
      _typeParameterList = typeParameterList
    }
    _typeParameterList
  }

  override def getReturnType: PsiType = {
    if (_returnType == NullPsiType) {
      _returnType = returnType
    }
    _returnType
  }

  override def getParameterList: PsiParameterList = {
    if (_parameterList == null) {
      _parameterList = parameterList
    }
    _parameterList
  }

  private def returnType = Option(returnScType).map(_.toPsiType).orNull

  private def parameterList: PsiParameterList = new ScLightParameterList(myManager, containingClass.getLanguage, parameters)

  private def typeParameterList: PsiTypeParameterList = {
    val list = new LightTypeParameterListBuilder(myManager, getLanguage)
    typeParameters.foreach(list.addParameter)
    list
  }

  override def getSignature(substitutor: PsiSubstitutor): MethodSignature = {
    MethodSignatureBackedByPsiMethod.create(this, substitutor)
  }

  override final def getParent: PsiElement = containingClass

  override final def findDeepestSuperMethods(): Array[PsiMethod] =
    PsiSuperMethodImplUtil.findDeepestSuperMethods(this)

  override final def findDeepestSuperMethod(): PsiMethod =
    PsiSuperMethodImplUtil.findDeepestSuperMethod(this)

  override def findSuperMethods(): Array[PsiMethod] =
    PsiMethod.EMPTY_ARRAY

  override final def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] =
    if (!checkAccess) findSuperMethods()
    else findSuperMethods().filterNot(_.hasModifierPropertyScala(PsiModifier.PRIVATE))

  override final def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] =
    findSuperMethods().filter(_.getContainingClass == parentClass)

  override final def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): util.List[MethodSignatureBackedByPsiMethod] =
    PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess)

  override final def getHierarchicalMethodSignature: HierarchicalMethodSignature =
    PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this)

  override def equals(other: Any): Boolean = other match {
    case that: PsiMethodWrapper[_] =>
      that.getName == getName &&
        that.delegate == delegate &&
        that.getContainingClass == getContainingClass
    case _ => false
  }

  override def hashCode(): Int =
    getName #+ delegate #+ getContainingClass
}

object PsiMethodWrapper {

  /**
   * Scala 2.13 scalac erases a class type parameter used directly as the return type of an inherited static forwarder.
   * Nested occurrences are still substituted and exposed through the generic signature.
   *
   * For example, given this Scala code:
   * {{{
   * final class Encoder[A]
   *
   * class SparkModel[A] {
   *   final val encoder: Encoder[A] = new Encoder[A]
   *   def value: A = ???
   * }
   *
   * final class User
   * object User extends SparkModel[User]
   * }}}
   *
   * scalac generates static forwarders in `User` equivalent to these Java methods:
   * {{{
   * public static Encoder<User> encoder() {
   *   return User$.MODULE$.encoder();
   * }
   *
   * public static Object value() {
   *   return User$.MODULE$.value();
   * }
   * }}}
   *
   * The JVM descriptor of `value` is `()Ljava/lang/Object;`.<br>
   * The JVM descriptor of `encoder` is `()LEncoder;`, with the generic signature `()LEncoder<LUser;>;` preserving the substitution.
   *
   * This is a JVM representation detail, not a Scala source-level typing rule. The Scala 2.13
   * specification defines type erasure: an abstract type erases to its upper bound, while a
   * parameterized type erases to its type constructor. JVM method descriptors contain only these
   * erased types; the optional `Signature` attribute carries generic information.
   * Static forwarders are compiler-generated methods in mirror classes, and their generation is
   * controlled by scalac's `-Xno-forwarders` option rather than prescribed by the Scala specification.
   *
   * In scalac 2.13, a direct `A` return cannot retain the `User` substitution in the forwarder's
   * generic signature without making that signature inconsistent with the erased descriptor. A nested
   * `Encoder[A]` type still erases to `Encoder`, so its generic signature can safely retain
   * `Encoder<User>`. This method mirrors that behavior by using the type parameter's upper bound
   * only for a direct inherited static-forwarder return and applying the substitutor in all other cases.
   *
   * The inheritance is important. With the same `Encoder` and `User` definitions, but with the
   * members declared directly in the object:
   * {{{
   * final class Encoder[A]
   *
   * final class User
   * object User {
   *   final val encoder: Encoder[User] = new Encoder[User]
   *   def value: User = ???
   * }
   * }}}
   *
   * the generated forwarders are equivalent to:
   * {{{
   * public static Encoder<User> encoder() {
   *   return User$.MODULE$.encoder();
   * }
   *
   * public static User value() {
   *   return User$.MODULE$.value();
   * }
   * }}}
   *
   * Here `value` is already a concrete `User` return type, so it is emitted as `User`.
   * The special erasure handling is therefore needed only when the return type is an inherited class
   * type parameter.
   *
   * ## References:
   *  - Scala 2.13 Type Erasure:<br>
   *    [[https://www.scala-lang.org/files/archive/spec/2.13/03-types.html#type-erasure]]
   *  - JVM Specification sections 4.3.3 and 4.7.9:<br>
   *    [[https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html]]
   *  - Scala 2.13 compiler's `BCodeHelpers.addForwarder`:<br>
   *    [[https://github.com/scala/scala/blob/v2.13.18/src/compiler/scala/tools/nsc/backend/jvm/BCodeHelpers.scala]]
   */
  private[light] def substituteReturnType(
    originalType: ScType,
    substitutor: ScSubstitutor,
    isInheritedStaticForwarder: Boolean
  ): ScType = originalType match {
    case typeParameter: TypeParameterType
      if isInheritedStaticForwarder && substitutor.isApplicableToTypeParam(typeParameter.typeParameter) =>
      typeParameter.upperType
    case _ =>
      substitutor(originalType)
  }

  def containingClass(delegate: ScNamedElement, concreteClass: Option[PsiClass], isStatic: Boolean): PsiClass = {
    val result = concreteClass.getOrElse(containingClass(delegate, isStatic))
    assert(
      result != null,
      s"""Member: ${delegate.getText}
         |has null containing class. isStatic: $isStatic
         |Containing file text: ${delegate.getContainingFile.getText}""".stripMargin
    )
    result
  }

  private def containingClass(delegate: ScNamedElement, isStatic: Boolean): PsiClass = {
    delegate.nameContext match {
      case s: ScMember =>
        val res = s.containingClass match {
          case null => s.syntheticContainingClass
          case clazz => clazz
        }
        if (isStatic) {
          res match {
            case o: ScObject => o.fakeCompanionClassOrCompanionClass
            case _ => res
          }
        } else res
      case _ => null
    }
  }
}
