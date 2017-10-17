package org.jetbrains.plugins.scala.lang
package psi
package types

package object result {

  implicit class TypeableExt(val typeable: ScalaPsiElement with Typeable) extends AnyVal {
    def success(`type`: ScType): Success[ScType] =
      Success(`type`, Some(typeable))(typeable.projectContext)
  }

}
