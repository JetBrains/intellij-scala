package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.stubs.{NamedStub, StubElement}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.CheapRefSearcher
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.CheapRefSearcher.ElementUsage
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import javax.swing.Icon
import scala.annotation.tailrec

trait ScNamedElement extends ScalaPsiElement with PsiNameIdentifierOwner with NavigatablePsiElement {

  @Cached(ModTracker.anyScalaPsiChange, this)
  def name: String = {
    this match {
      case st: StubBasedPsiElementBase[_] => st.getGreenStub match {
        case namedStub: NamedStub[_] => namedStub.getName
        case _ => nameInner
      }
      case _ => nameInner
    }
  }

  def nameInner: String = nameId.getText

  @Cached(ModTracker.anyScalaPsiChange, this)
  def nameContext: PsiElement = {
    @tailrec
    def byStub(stub: StubElement[_]): PsiElement = {
      if (stub == null) null
      else {
        val psi = stub.getPsi.asInstanceOf[PsiElement]

        if (isNameContext(psi)) psi
        else byStub(stub.getParentStub)
      }
    }

    @tailrec
    def byAST(element: PsiElement): PsiElement =
      if (element == null || isNameContext(element)) element
      else byAST(element.getParent)

    this match {
      case st: StubBasedPsiElementBase[_] =>
        val stub = st.getStub.asInstanceOf[StubElement[_]]

        if (stub != null) byStub(stub)
        else byAST(this)
      case _ => byAST(this)
    }
  }

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getName: String = ScalaNamesUtil.toJavaName(name)

  def nameId: PsiElement

  override def getNameIdentifier: PsiIdentifier = if (nameId != null) new JavaIdentifier(nameId) else null

  override def getIdentifyingElement: PsiElement = nameId

  override def setName(name: String): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    val newId = createIdentifier(name)
    parent.replaceChild(id, newId)
    this
  }

  override def getPresentation: ItemPresentation = {
    val clazz: ScTemplateDefinition = nameContext match {
      case null =>
        // can be null e.g. in bad `for` generator: for { x } {} (notice no `<-`)
        null
      case context =>
        context.getParent match {
          case _: ScTemplateBody | _: ScEarlyDefinitions =>
            PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition], true)
          case _ if this.isInstanceOf[ScClassParameter]  =>
            PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition], true)
          case _ => null
        }
    }
    val parentMember = Option(PsiTreeUtil.getParentOfType(this, classOf[ScMember], false))
    new ItemPresentation {
      override def getPresentableText: String = name
      override def getLocationString: String = clazz match {
        case _: ScTypeDefinition => "(" + clazz.qualifiedName + ")"
        case _: ScNewTemplateDefinition => "(<anonymous>)"
        case _ => parentMember.map(m => StringUtil.first(m.getText, 30, true)).getOrElse("")
      }
      override def getIcon(open: Boolean): Icon = parentMember.map(_.getIcon(0)).orNull
    }
  }

  override def getIcon(flags: Int): Icon =
    nameContext match {
      case null => null
      case _: ScCaseClause => Icons.PATTERN_VAL
      case x => x.getIcon(flags)
    }

  /**
   * Please read [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.CheapRefSearcher]] docs
   * before considering using this method.
   */
  // TODO Ideally org.jetbrains.plugins.scala.lang.psi has no dependencies on org.jetbrains.plugins.scala.codeInspection
  //  so that our PSI implementation can function as an independent module.
  //  For the reason of caching, for now this method lives here. If there is another feasible way to express
  //  that some cached result must be invalidated upon any PSI change in the project, we should implement that way.
  @Cached(ModTracker.physicalPsiChange(getProject), this)
  def getUsages(isOnTheFly: Boolean, reportPublicDeclarations: Boolean): Seq[ElementUsage] =
    CheapRefSearcher.search(this, isOnTheFly, reportPublicDeclarations)
}