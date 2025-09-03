package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.ir.typeTree.{TypeTree, TypeTreeHolder}

package object stubs {

  private[stubs] type RawStubElement = StubElement[_ <: PsiElement]
  private[stubs] type RawStubElementType = IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement]

  final def classNames(te: TypeTreeHolder): Array[String] = classNames(te.typeTree)
  final def classNames(te: TypeTree): Array[String] = {
    ???
  }

}
