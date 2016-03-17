package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.MethodImplementor
import com.intellij.codeInsight.generation.{GenerationInfo, PsiGenerationInfo}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMember, PsiMethod}
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScSubstitutor}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 12/25/13
 */
class ScalaMethodImplementor extends MethodImplementor {
  val emptyConsumer: Consumer[PsiMethod] = new Consumer[PsiMethod] {
    def consume(t: PsiMethod) {}
  }

  private val prototypeToBaseMethod = mutable.WeakHashMap[PsiMethod, PsiMethod]()

  def createImplementationPrototypes(inClass: PsiClass, method: PsiMethod): Array[PsiMethod] = {
    (for {
      td <- inClass.asOptionOf[ScTemplateDefinition].toSeq
      member <- ScalaOIUtil.getMembersToImplement(td).collect {case mm: ScMethodMember if mm.getElement == method => mm}
    } yield {
      val specifyType = ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY
      val body = ScalaGenerationInfo.defaultValue(member.scType, inClass.getContainingFile)
      val prototype = ScalaPsiElementFactory
              .createOverrideImplementMethod(member.sign, inClass.getManager, needsOverrideModifier = true, specifyType, body)
      prototypeToBaseMethod += (prototype -> method)
      prototype
    }).toArray
  }

  def createGenerationInfo(method: PsiMethod, mergeIfExists: Boolean): GenerationInfo = {
    val baseMethod = prototypeToBaseMethod.get(method)
    prototypeToBaseMethod.clear()
    new ScalaPsiMethodGenerationInfo(method, baseMethod.orNull)
  }

  def createDecorator(targetClass: PsiClass, baseMethod: PsiMethod, toCopyJavaDoc: Boolean, insertOverrideIfPossible: Boolean): Consumer[PsiMethod] = emptyConsumer

  def getMethodsToImplement(aClass: PsiClass): Array[PsiMethod] = Array()
}

private class ScalaPsiMethodGenerationInfo(method: PsiMethod, baseMethod: PsiMethod) extends PsiGenerationInfo[PsiMethod](method) {

  var member: PsiMember = method

  override def insert(aClass: PsiClass, anchor: PsiElement, before: Boolean) {
    aClass match {
      case td: ScTemplateDefinition =>
        val sign = new PhysicalSignature(method, ScSubstitutor.empty)(aClass.getProject.typeSystem)
        val methodMember = new ScMethodMember(sign, isOverride = false)

        member = ScalaGenerationInfo.insertMethod(methodMember, td, findAnchor(td, baseMethod))
      case _ => super.insert(aClass, anchor, before)
    }
  }

  override def positionCaret(editor: Editor, toEditMethodBody: Boolean) =
    member match {
      case _: ScMember => ScalaGenerationInfo.positionCaret(editor, member)
      case _ => super.positionCaret(editor, toEditMethodBody)
    }

  private def findAnchor(td: ScTemplateDefinition, baseMethod: PsiMethod): PsiElement = {
    if (baseMethod == null) return null

    var prevBaseMethod: PsiMethod = PsiTreeUtil.getPrevSiblingOfType(baseMethod, classOf[PsiMethod])

    while (prevBaseMethod != null) {
      td.findMethodBySignature(prevBaseMethod, checkBases = false) match {
        case wrapper: ScFunctionWrapper => return wrapper.function.getNextSibling
        case method: PsiMethod if method.isPhysical => return method.getNextSibling
        case _ =>
      }

      prevBaseMethod = PsiTreeUtil.getPrevSiblingOfType(prevBaseMethod, classOf[PsiMethod])
    }

    var nextBaseMethod: PsiMethod = PsiTreeUtil.getNextSiblingOfType(baseMethod, classOf[PsiMethod])

    while (nextBaseMethod != null) {
      td.findMethodBySignature(nextBaseMethod, checkBases = false) match {
        case wrapper: ScFunctionWrapper => return wrapper.function
        case method: PsiMethod if method.isPhysical => return method
        case _ =>
      }
      nextBaseMethod = PsiTreeUtil.getNextSiblingOfType(nextBaseMethod, classOf[PsiMethod])
    }

    null
  }
}
