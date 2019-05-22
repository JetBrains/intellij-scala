package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
  * @author adkozlov
  */
final class ScPropertyStubImpl[P <: ScValueOrVariable](parent: StubElement[_ <: PsiElement],
                                                       elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                       val isDeclaration: Boolean,
                                                       val isImplicit: Boolean,
                                                       val names: Array[String],
                                                       val typeText: Option[String],
                                                       val bodyText: Option[String],
                                                       val isLocal: Boolean,
                                                       val implicitType: Option[String])
  extends StubBase[P](parent, elementType) with ScPropertyStub[P]