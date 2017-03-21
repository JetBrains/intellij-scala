package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import javax.swing.Icon

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi._
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import com.intellij.psi.stubs.NamedStub
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

trait ScNamedElement extends ScalaPsiElement with PsiNameIdentifierOwner with NavigatablePsiElement {
  def name: String = {
    this match {
      case st: StubBasedPsiElementBase[_] =>  st.getGreenStub match {
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

  override def getName: String = ScalaNamesUtil.toJavaName(name)

  def nameId: PsiElement

  override def getNameIdentifier: PsiIdentifier = if (nameId != null) new JavaIdentifier(nameId) else null

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    val newId = createIdentifier(name)
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
        case _: ScNewTemplateDefinition => "(<anonymous>)"
        case _ => ""
      }
      override def getIcon(open: Boolean): Icon = parentMember match {case mem: ScMember => mem.getIcon(0) case _ => null}
    }
  }

  override def getIcon(flags: Int): Icon =
    ScalaPsiUtil.nameContext(this) match {
      case null => null
      case _: ScCaseClause => Icons.PATTERN_VAL
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
