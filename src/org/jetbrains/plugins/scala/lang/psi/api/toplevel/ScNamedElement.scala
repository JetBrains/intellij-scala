package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import expr.ScNewTemplateDefinition
import impl.toplevel.synthetic.JavaIdentifier
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import psi.ScalaPsiElement
import stubs.NamedStub
import templates.ScTemplateBody
import typedef._
import base.patterns.ScCaseClause
import icons.Icons
import psi.impl.ScalaPsiElementFactory
import reflect.NameTransformer

trait ScNamedElement extends ScalaPsiElement with PsiNameIdentifierOwner with NavigatablePsiElement {
  def name: String = {
    this match {
      case st: StubBasedPsiElement[_] =>  st.getStub match {
        case namedStub: NamedStub[_] => namedStub.getName
        case _ => nameInner
      }
      case _ => nameInner
    }
  }

  def name_=(it: String) {
    setName(it)
  }

  def nameInner: String = nameId.getText

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getName = NameTransformer.encode(name)

  def javaName = getName //todo: join with getName

  def nameId: PsiElement

  override def getNameIdentifier: PsiIdentifier = if (nameId != null) new JavaIdentifier(nameId) else null

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id, ScalaPsiElementFactory.createIdentifier(name, getManager))
    this
  }

  override def getPresentation: ItemPresentation = {
    val clazz: ScTemplateDefinition = if (getParent.isInstanceOf[ScTemplateBody])
      PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition], true)
    else null
    var parent: PsiElement = this
    while (parent != null && !(parent.isInstanceOf[ScMember])) parent = parent.getParent
    new ItemPresentation {
      def getPresentableText: String = name
      def getTextAttributesKey: TextAttributesKey = null
      def getLocationString: String = clazz match {
        case _: ScTypeDefinition => "(" + clazz.qualifiedName + ")"
        case x: ScNewTemplateDefinition => "(<anonymous>)"
        case _ => ""
      }
      override def getIcon(open: Boolean) = parent match {case mem: ScMember => mem.getIcon(0) case _ => null}
    }
  }

  override def getIcon(flags: Int) =
    ScalaPsiUtil.nameContext(this) match {
      case null => null
      case c: ScCaseClause => Icons.PATTERN_VAL
      case x => x.getIcon(flags)
    }
}