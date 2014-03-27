package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.MethodImplementor
import com.intellij.psi.{PsiMethod, PsiClass}
import com.intellij.util.Consumer
import com.intellij.codeInsight.generation.GenerationInfo
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, PhysicalSignature}
import extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 12/25/13
 */
class ScalaMethodImplementor extends MethodImplementor {
  val emptyConsumer: Consumer[PsiMethod] = new Consumer[PsiMethod] {
    def consume(t: PsiMethod) {}
  }

  def createImplementationPrototypes(inClass: PsiClass, method: PsiMethod): Array[PsiMethod] = {
    (for {
      td <- inClass.asOptionOf[ScTemplateDefinition].toSeq
      member <- ScalaOIUtil.getMembersToImplement(td).collect {case mm: ScMethodMember if mm.getElement == method => mm}
    } yield {
      val specifyType = ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY
      val body = ScalaGenerationInfo.defaultValue(member.scType, inClass.getContainingFile)
      ScalaPsiElementFactory.createOverrideImplementMethod(member.sign, inClass.getManager, needsOverrideModifier = true, specifyType, body)
    }).toArray
  }

  def createGenerationInfo(method: PsiMethod, mergeIfExists: Boolean): GenerationInfo = {
    val methodMember = new ScMethodMember(new PhysicalSignature(method, ScSubstitutor.empty), /*isImplement = */true)
    new ScalaGenerationInfo(methodMember)
  }

  def createDecorator(targetClass: PsiClass, baseMethod: PsiMethod, toCopyJavaDoc: Boolean, insertOverrideIfPossible: Boolean): Consumer[PsiMethod] = emptyConsumer

  def getMethodsToImplement(aClass: PsiClass): Array[PsiMethod] = Array()
}
