package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.javaView._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
 * @author ven
 */
object ScalaJVMNameMapper extends NameMapper {

  def getQualifiedName(clazz: PsiClass): String =
    clazz.getQualifiedName + clazz match {
      case _: ScObject => "$"
      case _: ScTrait => "$class"
      case _ => ""
    }

}