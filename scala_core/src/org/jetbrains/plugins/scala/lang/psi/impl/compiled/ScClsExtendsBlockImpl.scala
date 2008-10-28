package org.jetbrains.plugins.scala.lang.psi.impl.compiled

import api.statements.{ScFunction, ScTypeAlias}
import api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import com.intellij.psi.impl.compiled.ClsRepositoryPsiElement
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.{PsiElement, PsiClass, PsiElementVisitor}
import stubs.ScExtendsBlockStub
import toplevel.PsiClassFake
import types.{ScType, PhysicalSignature}


/**
 * @author ilyas
 */

class ScClsExtendsBlockImpl(stub: ScExtendsBlockStub)
extends ScClsElementImpl[ScExtendsBlock, ScExtendsBlockStub](stub) with ScExtendsBlock {

  //todo implement me!
  def setMirror(element: TreeElement): Unit = {}
  def appendMirrorText(indentLevel: Int, buffer: StringBuffer): Unit = {}
  def accept(visitor: PsiElementVisitor): Unit = {}
  protected def findChildrenByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = Array[T]()
  protected def findChildByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = null
  def templateBody: Option[ScTemplateBody] = None
  def supers: Seq[PsiClass] = Seq.empty
  def isAnonymousClass: Boolean = false
  def empty: Boolean = true

  def superTypes: Seq[ScType] = Seq.empty

  def typeDefinitions: Seq[ScTypeDefinition] = Seq.empty

  def aliases() = Seq.empty

  def functions() = Seq.empty

  def members: Seq[ScMember] = Seq.empty

  def directSupersNames: Seq[String] = Seq.empty
}