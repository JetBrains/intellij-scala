package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.MethodImplementor
import com.intellij.codeInsight.generation.{GenerationInfo, PsiGenerationInfo}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMember, PsiMethod}
import com.intellij.util.{Consumer, EmptyConsumer}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createOverrideImplementMethod
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalMethodSignature
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

import scala.annotation.nowarn
import scala.collection.mutable

class ScalaMethodImplementor extends MethodImplementor {
  private val prototypeToBaseMethod = mutable.WeakHashMap[PsiMethod, PsiMethod]()

  override def createImplementationPrototypes(inClass: PsiClass, method: PsiMethod): Array[PsiMethod] = {
    (for {
      td <- inClass.asOptionOf[ScTemplateDefinition].toSeq
      member <- ScalaOIUtil.getMembersToImplement(td).collect {
        case member@ScMethodMember(PhysicalMethodSignature(element, _), _) if element == method => member
      }
    } yield {
      val body = ScalaGenerationInfo.defaultValue
      val prototype = createOverrideImplementMethod(member.signature, needsOverrideModifier = true, body, inClass)(inClass.getManager)
      TypeAnnotationUtil.removeTypeAnnotationIfNeeded(prototype)
      prototypeToBaseMethod += (prototype -> method)
      prototype
    }).toArray
  }

  override def createGenerationInfo(method: PsiMethod, mergeIfExists: Boolean): GenerationInfo = {
    if (!method.isInstanceOf[ScalaPsiElement])
      return null
    val baseMethod = prototypeToBaseMethod.get(method)
    prototypeToBaseMethod.clear()
    new ScalaPsiMethodGenerationInfo(method, baseMethod.orNull)
  }

  @nowarn("msg=class EmptyConsumer in package util is deprecated")
  override def createDecorator(targetClass: PsiClass, baseMethod: PsiMethod, toCopyJavaDoc: Boolean, insertOverrideIfPossible: Boolean): Consumer[PsiMethod] = EmptyConsumer.getInstance()

  override def getMethodsToImplement(aClass: PsiClass): Array[PsiMethod] = Array()
}

private class ScalaPsiMethodGenerationInfo(method: PsiMethod, baseMethod: PsiMethod) extends PsiGenerationInfo[PsiMethod](method) {

  var member: PsiMember = method

  override def insert(aClass: PsiClass, anchor: PsiElement, before: Boolean): Unit = {
    aClass match {
      case td: ScTemplateDefinition =>
        val typeAdjuster = new TypeAdjuster()
        member = ScalaGenerationInfo.insertMethod(ScMethodMember(method), td, findAnchor(td, baseMethod), typeAdjuster)
        typeAdjuster.adjustTypes()
      case _ => super.insert(aClass, anchor, before)
    }
  }

  override def positionCaret(editor: Editor, toEditMethodBody: Boolean): Unit =
    member match {
      case _: ScMember => ScalaGenerationInfo.positionCaret(editor, member)
      case _ => super.positionCaret(editor, toEditMethodBody)
    }

  private def findAnchor(td: ScTemplateDefinition, baseMethod: PsiMethod): PsiElement = {
    if (baseMethod == null) return null

    var prevBaseMethod: PsiMethod = PsiTreeUtil.getPrevSiblingOfType(baseMethod, classOf[PsiMethod])

    while (prevBaseMethod != null) {
      td.findMethodBySignature(prevBaseMethod, false) match {
        case ScFunctionWrapper(delegate) => return delegate.getNextSibling
        case method: PsiMethod if method.isPhysical => return method.getNextSibling
        case _ =>
      }

      prevBaseMethod = PsiTreeUtil.getPrevSiblingOfType(prevBaseMethod, classOf[PsiMethod])
    }

    var nextBaseMethod: PsiMethod = PsiTreeUtil.getNextSiblingOfType(baseMethod, classOf[PsiMethod])

    while (nextBaseMethod != null) {
      td.findMethodBySignature(nextBaseMethod, false) match {
        case ScFunctionWrapper(delegate) => return delegate
        case method: PsiMethod if method.isPhysical => return method
        case _ =>
      }
      nextBaseMethod = PsiTreeUtil.getNextSiblingOfType(nextBaseMethod, classOf[PsiMethod])
    }

    null
  }
}
