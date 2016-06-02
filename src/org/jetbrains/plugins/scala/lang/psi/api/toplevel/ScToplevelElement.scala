package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.psi._
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable.ArrayBuffer

/**
 * Trait that implements logic by some type definitions aggregation
 *
 * @author ilyas
 */

trait ScToplevelElement extends ScalaPsiElement {
  def typeDefinitions: Seq[ScTypeDefinition] = {
    val buff = new ArrayBuffer[ScTypeDefinition]
    buff ++= immediateTypeDefinitions
    for (pack <- packagings) buff ++= pack.typeDefinitions
    buff.toSeq
  }

  def typeDefinitionsArray: Array[ScTypeDefinition] = {
    val buff = new ArrayBuffer[ScTypeDefinition]
    buff ++= immediateTypeDefinitions
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
      stub.getChildrenByType[ScTypeDefinition](getProject.tokenSets.templateDefinitionSet, JavaArrayFactoryUtil.ScTypeDefinitionFactory)
    } else findChildrenByClassScala(classOf[ScTypeDefinition]).toSeq
  }

  def packagings: Seq[ScPackaging] = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case file: PsiFileImpl => file.getStub
      case st: ScPackagingImpl => st.getStub
      case _ => null
    }
    if (stub != null) {
      stub.getChildrenByType[ScPackaging](ScalaElementTypes.PACKAGING, JavaArrayFactoryUtil.ScPackagingFactory)
    } else {
      val buffer = new ArrayBuffer[ScPackaging]
      var curr = getFirstChild
      while (curr != null) {
        curr match {
          case packaging: ScPackaging => buffer += packaging
          case _ =>
        }
        curr = curr.getNextSibling
      }
      buffer.toSeq
      //collection.immutable.Seq(findChildrenByClassScala(classOf[ScPackaging]).toSeq : _*)
    }
  }
}