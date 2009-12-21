package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi._
import stubs.StubElement
import impl.source.PsiFileImpl
import collection.mutable.ArrayBuffer
import psi.impl.toplevel.packaging.ScPackagingImpl

/**
 * Trait that implements logic by some type definitions aggregation
 *
 * @author ilyas
 */

trait ScToplevelElement extends ScalaPsiElement {
  def typeDefinitions(): Array[ScTypeDefinition] = {
    val buff = new ArrayBuffer[ScTypeDefinition]
    for (clazz <- immediateTypeDefinitions) buff += clazz
    for (pack <- packagings) buff ++= pack.typeDefinitions
    buff.toArray
  }

  def immediateTypeDefinitions: Seq[ScTypeDefinition] = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScPackagingImpl => st.getStub
      case _ => null
    }
    if (stub != null) {
      stub.getChildrenByType[ScTypeDefinition](TokenSets.TMPL_DEF_BIT_SET, new ArrayFactory[ScTypeDefinition] {
        def create(count: Int): Array[ScTypeDefinition] = new Array[ScTypeDefinition](count)
      })
    } else collection.immutable.Seq(findChildrenByClassScala(classOf[ScTypeDefinition]).toSeq : _*)
  }

  def packagings: Seq[ScPackaging] = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScPackagingImpl => st.getStub
      case _ => null
    }
    if (stub != null) {
      stub.getChildrenByType[ScPackaging](ScalaElementTypes.PACKAGING, new ArrayFactory[ScPackaging] {
        def create(count: Int): Array[ScPackaging] = new Array[ScPackaging](count)
      })
    } else collection.immutable.Seq(findChildrenByClassScala(classOf[ScPackaging]).toSeq : _*)
  }
}