package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.javaView._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
 * @author ven
 */
object ScalaJVMNameMapper extends NameMapper {
  def getQualifiedName(clazz : PsiClass) : String = {
    if (clazz.isInstanceOf[ScJavaClass]) {
      val scClass = clazz.asInstanceOf[ScJavaClass].scClass
      return scClass match {
        case _ : ScObject => scClass.getQualifiedName + "$"
        case _ : ScTrait => scClass.getQualifiedName + "$class"
        case _ => scClass.getQualifiedName
      }
    }
    null
  }
}