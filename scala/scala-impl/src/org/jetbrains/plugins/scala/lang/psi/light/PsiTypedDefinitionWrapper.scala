package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.PsiMethodWrapper.containingClass
import org.jetbrains.plugins.scala.lang.psi.types.api.{AnyRef, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TermSignature}

class PsiTypedDefinitionWrapper(override val delegate: ScTypedDefinition,
                                isStatic: Boolean,
                                isAbstract: Boolean,
                                role: DefinitionRole,
                                cClass: Option[PsiClass] = None)
  extends PsiMethodWrapper(delegate, javaMethodName(delegate.name, role), containingClass(delegate, cClass, isStatic)) {

  override def getNameIdentifier: PsiIdentifier = delegate.getNameIdentifier

  override def isWritable: Boolean = getContainingFile.isWritable

  override def setName(name: String): PsiElement = {
    if (role == SIMPLE_ROLE) delegate.setName(name)
    else this
  }

  override protected def returnScType: ScType = PsiTypedDefinitionWrapper.typeFor(delegate, role)

  override protected def parameters: Seq[PsiParameter] = PsiTypedDefinitionWrapper.propertyMethodParameters(delegate, role, None)

  override protected def typeParameters: Seq[PsiTypeParameter] = Seq.empty

  override def modifierList: PsiModifierList =
    ScLightModifierList(delegate, isStatic, isAbstract, getContainingClass.isInstanceOf[ScTrait])

  override def findSuperMethods(): Array[PsiMethod] = {
    if (isStatic)
      return PsiMethod.EMPTY_ARRAY

    def wrap(superSig: TermSignature): Option[PsiMethod] = superSig.namedElement match {
      case f: ScFunction =>
        Some(new ScFunctionWrapper(f, isStatic, isAbstract = f.isAbstractMember, cClass = None, isJavaVarargs = false))
      case td: ScTypedDefinition =>
        Some(new PsiTypedDefinitionWrapper(td, isStatic, isAbstract = td.isAbstractMember, role))
      case m: PsiMethod =>
        Some(m)
      case _ => None
    }

    val name = PropertyMethods.methodName(delegate.name, role)
    val superSignatures =
      TypeDefinitionMembers.getSignatures(getContainingClass)
        .forName(name)
        .findNode(delegate)
        .map(_.supers.map(_.info))

    superSignatures.getOrElse(Seq.empty).flatMap(wrap).toArray
  }

  override def copy(): PsiElement =
    new PsiTypedDefinitionWrapper(delegate.asInstanceOf[ScTypedDefinition], isStatic, isAbstract, role, cClass)
}

object PsiTypedDefinitionWrapper {

  def unapply(wrapper: PsiTypedDefinitionWrapper): Option[ScTypedDefinition] = Some(wrapper.delegate)

  def processWrappersFor(t: ScTypedDefinition, cClass: Option[PsiClass], nodeName: String, isStatic: Boolean, isInterface: Boolean,
                 processMethod: PsiMethod => Unit, processName: String => Unit = _ => ()): Unit  = {
    val scalaName = t.name
    val roleByName = methodRole(nodeName, scalaName)

    roleByName
      .filter(isApplicable(_, t, noResolve = false))
      .foreach { role =>
        processMethod(t.getTypedDefinitionWrapper(isStatic, isInterface, role, cClass))
        processName(javaMethodName(scalaName, role))
      }
  }

  private[light] def propertyMethodParameters(td: ScTypedDefinition, role: DefinitionRole, staticTrait: Option[PsiClassWrapper]): Seq[PsiParameter] = {
    val thisParam = staticTrait.map(ScLightParameter.fromThis(_, td))

    val setterParam =
      if (!isSetter(role)) None
      else Some(new ScLightParameter(td.getName, () => typeFor(td, SIMPLE_ROLE).toPsiType, td))

    thisParam.toSeq ++ setterParam
  }


  def typeFor(typedDefinition: ScTypedDefinition, role: DefinitionRole): ScType = {
    import typedDefinition.projectContext

    if (role == SETTER || role == EQ) Unit
    else
      typedDefinition match {
      case param: ScParameter => param.getRealParameterType.getOrElse(AnyRef)
      case other              => other.`type`().getOrElse(AnyRef)
    }
  }
}
