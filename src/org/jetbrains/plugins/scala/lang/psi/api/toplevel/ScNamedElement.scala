package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.plugins.scala.lang.psi.api.expr._
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
import statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import scala.Some

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

  override def getName = ScalaNamesUtil.toJavaName(name)

  def nameId: PsiElement

  override def getNameIdentifier: PsiIdentifier = if (nameId != null) new JavaIdentifier(nameId) else null

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    val newId = ScalaPsiElementFactory.createIdentifier(name, getManager)
    parent.replaceChild(id, newId)
    this
  }

  override def getPresentation: ItemPresentation = {
    val clazz: ScTemplateDefinition =
      getParent match {
        case _: ScTemplateBody | _: ScEarlyDefinitions =>
          PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition], true)
        case _ if this.isInstanceOf[ScClassParameter]  =>
          PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition], true)
        case _ => null
      }

    val parentMember: ScMember = PsiTreeUtil.getParentOfType(this, classOf[ScMember], false)
    new ItemPresentation {
      def getPresentableText: String = name
      def getTextAttributesKey: TextAttributesKey = null
      def getLocationString: String = clazz match {
        case _: ScTypeDefinition => "(" + clazz.qualifiedName + ")"
        case x: ScNewTemplateDefinition => "(<anonymous>)"
        case _ => ""
      }
      override def getIcon(open: Boolean) = parentMember match {case mem: ScMember => mem.getIcon(0) case _ => null}
    }
  }

  override def getIcon(flags: Int) =
    ScalaPsiUtil.nameContext(this) match {
      case null => null
      case c: ScCaseClause => Icons.PATTERN_VAL
      case x => x.getIcon(flags)
    }

  abstract override def getUseScope: SearchScope = {
    ScalaPsiUtil.intersectScopes(super.getUseScope, ScalaPsiUtil.nameContext(this) match {
      case member: ScMember if member != this => Some(member.getUseScope)
      case caseClause: ScCaseClause => Some(new LocalSearchScope(caseClause))
      case elem @ (_: ScEnumerator | _: ScGenerator) =>
        Option(PsiTreeUtil.getContextOfType(elem, true, classOf[ScForStatement]))
                .orElse(Option(PsiTreeUtil.getContextOfType(elem, true, classOf[ScBlock], classOf[ScMember])))
                .map(new LocalSearchScope(_))
      case _ => None
    })
  }
}