package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}

/**
 * User: Alefas
 * Date: 18.02.12
 */
class PsiTypedDefinitionWrapper(val typedDefinition: ScTypedDefinition, isStatic: Boolean, isInterface: Boolean,
                                role: PsiTypedDefinitionWrapper.DefinitionRole.DefinitionRole,
                                cClass: Option[PsiClass] = None) extends {
  val elementFactory = JavaPsiFacade.getInstance(typedDefinition.getProject).getElementFactory
  val containingClass = {
    val result = if (cClass != None) cClass.get
    else
      typedDefinition.nameContext match {
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
    if (result == null) {
      val message = "Containing class is null: " + typedDefinition.getContainingFile.getText + "\n" +
        "typed Definition: " + typedDefinition.getTextRange.getStartOffset
      throw new RuntimeException(message)
    }
    result
  }
  val methodText = PsiTypedDefinitionWrapper.methodText(typedDefinition, isStatic, isInterface, role)
  val method: PsiMethod = {
    try {
      elementFactory.createMethodFromText(methodText, containingClass)
    } catch {
      case _: Exception => elementFactory.createMethodFromText("public void FAILED_TO_DECOMPILE_METHOD() {}", containingClass)
    }
  }
} with LightMethodAdapter(typedDefinition.getManager, method, containingClass) with LightScalaMethod {

  override def getNavigationElement: PsiElement = this

  override def canNavigate: Boolean = typedDefinition.canNavigate

  override def canNavigateToSource: Boolean = typedDefinition.canNavigateToSource

  override def navigate(requestFocus: Boolean): Unit = typedDefinition.navigate(requestFocus)

  override def getTextRange: TextRange = typedDefinition.getTextRange

  override def getTextOffset: Int = typedDefinition.getTextOffset

  override def getParent: PsiElement = containingClass

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case "abstract" if isInterface => true
      case _ => super.hasModifierProperty(name)
    }
  }

  override def getPrevSibling: PsiElement = typedDefinition.getPrevSibling

  override def getNextSibling: PsiElement = typedDefinition.getNextSibling

  override def getNameIdentifier: PsiIdentifier = typedDefinition.getNameIdentifier

  override def isWritable: Boolean = getContainingFile.isWritable

  override def setName(name: String): PsiElement = {
    if (role == PsiTypedDefinitionWrapper.DefinitionRole.SIMPLE_ROLE) typedDefinition.setName(name)
    else this
  }
}

object PsiTypedDefinitionWrapper {
  object DefinitionRole extends Enumeration {
    type DefinitionRole = Value
    val SIMPLE_ROLE, GETTER, IS_GETTER, SETTER, EQ = Value
  }
  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  def methodText(b: ScTypedDefinition, isStatic: Boolean, isInterface: Boolean, role: DefinitionRole): String = {
    val builder = new StringBuilder

    ScalaPsiUtil.nameContext(b) match {
      case m: ScModifierListOwner =>
        builder.append(JavaConversionUtil.annotationsAndModifiers(m, isStatic))
      case _ =>
    }

    val result = b.getType(TypingContext.empty)
    result match {
      case _ if role == SETTER || role == EQ => builder.append("void")
      case Success(tp, _) => builder.append(JavaConversionUtil.typeText(tp, b.getProject, b.getResolveScope))
      case _ => builder.append("java.lang.Object")
    }

    builder.append(" ")
    val name = role match {
      case SIMPLE_ROLE => b.getName
      case GETTER => "get" + b.getName.capitalize
      case IS_GETTER => "is" + b.getName.capitalize
      case SETTER => "set" + b.getName.capitalize
      case EQ => b.getName + "_$eq"
    }
    builder.append(name)
    if (role != SETTER && role != EQ) {
      builder.append("()")
    } else {
      builder.append("(")
      result match {
        case Success(tp, _) => builder.append(JavaConversionUtil.typeText(tp, b.getProject, b.getResolveScope))
        case _ => builder.append("java.lang.Object")
      }
      builder.append(" ").append(b.getName).append(")")
    }

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
}
