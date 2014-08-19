package org.jetbrains.plugins.scala
package extensions.implementation

import com.intellij.psi.{PsiClass, PsiMethod, PsiModifier}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * User: Alefas
 * Date: 03.02.12
 */
class PsiClassExt(clazz: PsiClass) {
  /**
   * Second match branch is for Java only.
   */
  def qualifiedName: String = {
    clazz match {
      case t: ScTemplateDefinition => t.qualifiedName
      case _ => clazz.getQualifiedName
    }
  }

  def constructors: Array[PsiMethod] = {
    clazz match {
      case c: ScClass => c.constructors
      case _ => clazz.getConstructors
    }
  }

  def isEffectivelyFinal: Boolean = clazz match {
    case scClass: ScClass => scClass.hasFinalModifier
    case _: ScObject => true
    case synth: ScSyntheticClass if !Seq("AnyRef", "AnyVal").contains(synth.className) => true //wrappers for value types
    case _ => clazz.hasModifierProperty(PsiModifier.FINAL)
  }


  def processPsiMethodsForNode(node: SignatureNodes.Node, isStatic: Boolean, isInterface: Boolean)
                              (processMethod: PsiMethod => Unit, processName: String => Unit = _ => ()): Unit = {

    def concreteClassFor(typedDef: ScTypedDefinition): Option[PsiClass] = {
      if (typedDef.isAbstractMember) return None
      clazz match {
        case wrapper: PsiClassWrapper if wrapper.definition.isInstanceOf[ScObject] =>
          return Some(wrapper.definition)
        case _ =>
      }

      ScalaPsiUtil.nameContext(typedDef) match {
        case m: ScMember =>
          m.containingClass match {
            case t: ScTrait =>
              val linearization = MixinNodes.linearization(clazz).flatMap(tp => ScType.extractClass(tp, Some(clazz.getProject)))
              var index = linearization.indexWhere(_ == t)
              while (index >= 0) {
                val cl = linearization(index)
                if (!cl.isInterface) return Some(cl)
                index -= 1
              }
              Some(clazz)
            case _ => None
          }
        case _ => None
      }
    }

    node.info.namedElement match {
      case fun: ScFunction if !fun.isConstructor =>
        val wrappers = fun.getFunctionWrappers(isStatic, isInterface = fun.isAbstractMember, concreteClassFor(fun))
        wrappers.foreach(processMethod)
        processName(fun.name)
      case method: PsiMethod if !method.isConstructor =>
        if (isStatic) {
          if (method.containingClass != null && method.containingClass.qualifiedName != "java.lang.Object") {
            processMethod(StaticPsiMethodWrapper.getWrapper(method, clazz))
            processName(method.getName)
          }
        }
        else {
          processMethod(method)
          processName(method.getName)
        }
      case t: ScTypedDefinition if t.isVal || t.isVar ||
              (t.isInstanceOf[ScClassParameter] && t.asInstanceOf[ScClassParameter].isCaseClassVal) =>

        PsiTypedDefinitionWrapper.processWrappersFor(t, concreteClassFor(t), node.info.name, isStatic, isInterface, processMethod, processName)
      case _ =>
    }
  }
}
