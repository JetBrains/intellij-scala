package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, ScType, TermSignature}
import org.jetbrains.plugins.scala.lang.psi.types.api.{AnyRef, Unit}

/**
 * User: Alefas
 * Date: 18.02.12
 */
class PsiTypedDefinitionWrapper(val delegate: ScTypedDefinition, isStatic: Boolean, isInterface: Boolean,
                                role: DefinitionRole,
                                cClass: Option[PsiClass] = None) extends {
  val containingClass: PsiClass = {
    val result = cClass.getOrElse {
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

    if (result == null) {
      val message = "Containing class is null: " + delegate.getContainingFile.getText + "\n" +
        "typed Definition: " + delegate.getTextRange.getStartOffset
      throw new RuntimeException(message)
    }
    result
  }

  val method: PsiMethod = {
    val methodText = PsiTypedDefinitionWrapper.methodText(delegate, isStatic, isInterface, role)
    LightUtil.createJavaMethod(methodText, containingClass, delegate.getProject)
  }

} with PsiMethodWrapper(delegate.getManager, method, containingClass)
  with NavigablePsiElementWrapper[ScTypedDefinition] {

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "abstract" if isInterface => true
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getNameIdentifier: PsiIdentifier = delegate.getNameIdentifier

  override def isWritable: Boolean = getContainingFile.isWritable

  override def setName(name: String): PsiElement = {
    if (role == SIMPLE_ROLE) delegate.setName(name)
    else this
  }

  override protected def returnType: ScType = PsiTypedDefinitionWrapper.typeFor(delegate, role)

  override protected def parameterListText: String = PsiTypedDefinitionWrapper.parameterListText(delegate, role, None)

  override def findSuperMethods(): Array[PsiMethod] = {
    if (isStatic)
      return PsiMethod.EMPTY_ARRAY

    def wrap(superSig: TermSignature): Option[PsiMethod] = superSig.namedElement match {
      case f: ScFunction =>
        Some(new ScFunctionWrapper(f, isStatic, isInterface = f.isAbstractMember, cClass = None, isJavaVarargs = false))
      case td: ScTypedDefinition =>
        Some(new PsiTypedDefinitionWrapper(td, isStatic, isInterface = td.isAbstractMember, role))
      case m: PsiMethod =>
        Some(m)
      case _ => None
    }

    val name = PropertyMethods.methodName(delegate.name, role)
    val superSignatures =
      TypeDefinitionMembers.getSignatures(containingClass)
        .forName(name)
        .findNode(delegate)
        .map(_.supers.map(_.info))

    superSignatures.getOrElse(Seq.empty).flatMap(wrap).toArray
  }
}

object PsiTypedDefinitionWrapper {

  def unapply(wrapper: PsiTypedDefinitionWrapper): Option[ScTypedDefinition] = Some(wrapper.delegate)

  def methodText(b: ScTypedDefinition, isStatic: Boolean, isInterface: Boolean, role: DefinitionRole): String = {
    val builder = new StringBuilder

    ScalaPsiUtil.nameContext(b) match {
      case m: ScModifierListOwner =>
        builder.append(JavaConversionUtil.annotationsAndModifiers(m, isStatic))
      case _ =>
    }

    builder.append("java.lang.Object")

    builder.append(" ")
    val name = javaMethodName(b.name, role)
    builder.append(name)
    builder.append("()")

    val holder = PsiTreeUtil.getContextOfType(b, classOf[ScAnnotationsHolder])
    if (holder != null) {
      builder.append(LightUtil.getThrowsSection(holder))
    }

    if (!isInterface)
      builder.append(" {}")
    else
      builder.append(";")

    builder.toString()
  }

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

  private[light] def parameterListText(td: ScTypedDefinition, role: DefinitionRole, staticTrait: Option[PsiClassWrapper]): String = {
    val thisParam = staticTrait.map { trt =>
      val qualName = trt.getQualifiedName
      qualName.stripSuffix("$class") + " This"
    }

    val params =
      if (!isSetter(role)) Nil
      else {
        val paramType = typeFor(td, SIMPLE_ROLE)
        val typeText = JavaConversionUtil.typeText(paramType)
        val name = td.getName
        Seq(s"$typeText $name")
      }

    (thisParam ++: params).mkString("(", ", ", ")")
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
