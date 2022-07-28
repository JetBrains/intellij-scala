package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final class ScFunctionStubImpl[F <: ScFunction](
  parent:                                        StubElement[_ <: PsiElement],
  elementType:                                   IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
  name:                                          String,
  override val isDeclaration:                    Boolean,
  override val annotations:                      Array[String],
  override val typeText:                         Option[String],
  override val bodyText:                         Option[String],
  override val hasAssign:                        Boolean,
  override val implicitConversionParameterClass: Option[String],
  override val isLocal:                          Boolean,
  override val implicitClassNames:               Array[String],
  override val isTopLevel:                       Boolean,
  override val topLevelQualifier:                Option[String],
  override val isExtensionMethod:                Boolean,
  override val isGiven:                          Boolean,
  override val givenClassNames:                  Array[String],
) extends ScNamedStubBase[F](parent, elementType, name) with ScFunctionStub[F]
