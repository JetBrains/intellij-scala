package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.javaView._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._

/**
 * @author ven
 */
object ScalaJVMNameMapper extends NameMapper {
  def getQualifiedName(clazz : PsiClass) = {
    if (clazz.isInstanceOf[ScJavaClass]) {
      val scClass = clazz.asInstanceOf[ScJavaClass].scClass
      scClass match {
        case _ : ScObjectDefinition => scClass.getQualifiedName + "$"
        case _ => scClass.getQualifiedName
      }
    }
    null
  }
}