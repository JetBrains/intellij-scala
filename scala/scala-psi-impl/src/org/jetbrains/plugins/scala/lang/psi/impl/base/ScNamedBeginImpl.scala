package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.util.chaining._

trait ScNamedBeginImpl extends ScBegin with ScNamedElement {
  override def setName(name: String): PsiElement =
    super.setName(name)
      .tap { renamed =>
        renamed.asOptionOf[ScBegin]
          .flatMap(_.end)
          .foreach(_.setName(name))
      }
}
