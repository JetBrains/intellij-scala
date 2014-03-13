package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod, JavaPsiFacade}

/**
 * User: Alefas
 * Date: 20.02.12
 */
class EmptyPrivateConstructor(o: PsiClass) extends {
  val elementFactory = JavaPsiFacade.getInstance(o.getProject).getElementFactory
  val constructorText = "private " + Option(o.getName).map {
    case e if JavaPsiFacade.getInstance(o.getProject).getNameHelper.isIdentifier(o.getName) => o.getName
    case _ => "METHOD_NAME_IS_NOT_AN_IDENTIFIER"
  }.get + "() {}"
  val method: PsiMethod = elementFactory.createMethodFromText(constructorText, o)
} with LightMethodAdapter(o.getManager, method, o) {
  override def getParent: PsiElement = o
}
