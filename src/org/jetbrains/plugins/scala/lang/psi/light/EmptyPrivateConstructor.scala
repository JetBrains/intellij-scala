package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * User: Alefas
 * Date: 20.02.12
 */
class EmptyPrivateConstructor(o: PsiClass) extends {
  val method: PsiMethod = {
    val constructorText = "private " + Option(o.getName).map {
      case _ if PsiNameHelper.getInstance(o.getProject).isIdentifier(o.getName) => o.getName
      case _ => "METHOD_NAME_IS_NOT_AN_IDENTIFIER"
    }.get + "() {}"
    LightUtil.createJavaMethod(constructorText, o, o.getProject)
  }
} with PsiMethodWrapper(o.getManager, method, o) {

  override protected def returnType: ScType = null

  override protected def parameterListText: String = "()"
}
