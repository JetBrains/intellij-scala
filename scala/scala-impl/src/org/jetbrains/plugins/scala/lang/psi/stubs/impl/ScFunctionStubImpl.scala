package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
final class ScFunctionStubImpl[F <: ScFunction](parent: StubElement[_ <: PsiElement],
                                                elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                name: String,
                                                val isDeclaration: Boolean,
                                                val annotations: Array[String],
                                                val typeText: Option[String],
                                                val bodyText: Option[String],
                                                val hasAssign: Boolean,
                                                val isImplicitConversion: Boolean,
                                                val isLocal: Boolean,
                                                val implicitType: Option[String])
  extends ScNamedStubBase[F](parent, elementType, name) with ScFunctionStub[F]