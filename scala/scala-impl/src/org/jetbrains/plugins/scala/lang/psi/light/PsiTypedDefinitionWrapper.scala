package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{AnyRef, Unit}

/**
 * User: Alefas
 * Date: 18.02.12
 */
class PsiTypedDefinitionWrapper(val delegate: ScTypedDefinition, isStatic: Boolean, isInterface: Boolean,
                                role: DefinitionRole.DefinitionRole,
                                cClass: Option[PsiClass] = None) extends {
  val containingClass: PsiClass = {
    val result = cClass.getOrElse {
      delegate.nameContext match {
        case s: ScMember =>
          val res = Option(s.containingClass).orElse(s.syntheticContainingClass).orNull
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
    if (role == DefinitionRole.SIMPLE_ROLE) delegate.setName(name)
    else this
  }

  override protected def returnType: ScType = PsiTypedDefinitionWrapper.typeFor(delegate, role)

  override protected def parameterListText: String = PsiTypedDefinitionWrapper.parameterListText(delegate, role, None)
}

object PsiTypedDefinitionWrapper {

  def unapply(wrapper: PsiTypedDefinitionWrapper): Option[ScTypedDefinition] = Some(wrapper.delegate)

  object DefinitionRole extends Enumeration {
    type DefinitionRole = Value
    val SIMPLE_ROLE, GETTER, IS_GETTER, SETTER, EQ = Value

    def isSetter(role: DefinitionRole): Boolean = role == SETTER || role == EQ
  }
  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  def methodText(b: ScTypedDefinition, isStatic: Boolean, isInterface: Boolean, role: DefinitionRole): String = {
    val builder = new StringBuilder

    ScalaPsiUtil.nameContext(b) match {
      case m: ScModifierListOwner =>
        builder.append(JavaConversionUtil.annotationsAndModifiers(m, isStatic))
      case _ =>
    }

    builder.append("java.lang.Object")

    builder.append(" ")
    val name = role match {
      case SIMPLE_ROLE => b.getName
      case GETTER => "get" + b.getName.capitalize
      case IS_GETTER => "is" + b.getName.capitalize
      case SETTER => "set" + b.getName.capitalize
      case EQ => b.getName + "_$eq"
    }
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
    if (nodeName == t.name) {
      processMethod(t.getTypedDefinitionWrapper(isStatic, isInterface, role = SIMPLE_ROLE, cClass))
      processName(t.name)
      if (t.isVar) {
        processMethod(t.getTypedDefinitionWrapper(isStatic, isInterface, role = EQ, cClass))
        processName(t.name + "_eq")
      }
    }
    t.nameContext match {
      case s: ScAnnotationsHolder =>
        val beanProperty = ScalaPsiUtil.isBeanProperty(s)
        val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s)
        if (beanProperty) {
          if (nodeName == "get" + t.name.capitalize) {
            processMethod(t.getTypedDefinitionWrapper(isStatic, isInterface, role = GETTER, cClass))
            processName("get" + t.getName.capitalize)
          }
          if (t.isVar && nodeName == "set" + t.name.capitalize) {
            processMethod(t.getTypedDefinitionWrapper(isStatic, isInterface, role = SETTER, cClass))
            processName("set" + t.getName.capitalize)
          }
        } else if (booleanBeanProperty) {
          if (nodeName == "is" + t.name.capitalize) {
            processMethod(t.getTypedDefinitionWrapper(isStatic, isInterface, role = IS_GETTER, cClass))
            processName("is" + t.getName.capitalize)
          }
          if (t.isVar && nodeName == "set" + t.name.capitalize) {
            processMethod(t.getTypedDefinitionWrapper(isStatic, isInterface, role = SETTER, cClass))
            processName("set" + t.getName.capitalize)
          }
        }
      case _ =>
    }
  }

  private[light] def parameterListText(td: ScTypedDefinition, role: DefinitionRole, staticTrait: Option[PsiClassWrapper]): String = {
    val thisParam = staticTrait.map { trt =>
      val qualName = trt.getQualifiedName
      qualName.stripSuffix("$class") + " This"
    }

    val params =
      if (!DefinitionRole.isSetter(role)) Nil
      else {
        val paramType = typeFor(td, DefinitionRole.SIMPLE_ROLE)
        val typeText = JavaConversionUtil.typeText(paramType)(td.elementScope)
        val name = td.getName
        Seq(s"$typeText $name")
      }

    (thisParam ++: params).mkString("(", ", ", ")")
  }

  def typeFor(typedDefinition: ScTypedDefinition, role: DefinitionRole): ScType = {
    import typedDefinition.projectContext

    if (role == SETTER || role == EQ) Unit
    else typedDefinition.`type`().getOrElse(AnyRef)
  }
}
