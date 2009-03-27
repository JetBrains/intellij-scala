package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.structureView.impl.java.InheritedMembersFilter
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.util.treeView.smartTree._
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import java.lang.String
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.structureView.elements.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import psi._
import psi.api.ScalaFile

/**
* @author Alexander.Podkhalyuz
* Date: 04.05.2008
*/

class ScalaStructureViewModel(private val myRootElement: ScalaFile) extends TextEditorBasedStructureViewModel(myRootElement) {

  protected def getPsiFile(): PsiFile = {
    return myRootElement;
  }

  @NotNull
  def getRoot(): StructureViewTreeElement = {
    return new ScalaFileStructureViewElement(myRootElement);
  }

  @NotNull
  def getGroupers(): Array[Grouper] = {
    return Grouper.EMPTY_ARRAY;
  }

  @NotNull
  def getSorters(): Array[Sorter] = {
    val res = new Array[Sorter](1)
    res(0) = Sorter.ALPHA_SORTER
    return res
  }

  @NotNull
  def getFilters(): Array[Filter] = {
    return Array[Filter](new ScalaInheritedMembersFilter)
  }

  override def isSuitable(element: PsiElement) = element != null && isSuitableElementImpl(element)

  override def shouldEnterElement(o: Object) = o match {
    case t : ScTypeDefinition => t.members.length > 0 || t.typeDefinitions.size > 0
    case _ => false
  }

  def isSuitableElementImpl(e: PsiElement): Boolean = e match {
    case t: ScTypeDefinition => t.getParent match {
      case _: ScalaFile | _: ScPackaging => true
      case _ => false
    }
    case f: ScFunction => f.getParent match {
      case b: ScBlockExpr => b.getParent.isInstanceOf[ScFunction]
      case tb: ScTemplateBody if (tb.getParent.isInstanceOf[ScExtendsBlock]) => {
        isSuitableElementImpl(tb.getParent.getParent)
      }
      case _ => false
    }
    case m: ScMember => m.getParent match {
      case tb: ScTemplateBody if (tb.getParent.isInstanceOf[ScExtendsBlock]) => {
        isSuitableElementImpl(tb.getParent.getParent)
      }
      case _ => false
    }
    case _ => false
  }



}

class ScalaInheritedMembersFilter extends InheritedMembersFilter {
  override def isVisible(treeNode: TreeElement): Boolean = {
    treeNode match {
      case x: ScalaFunctionStructureViewElement => !x.isInherited
      case x: ScalaValueStructureViewElement => !x.isInherited
      case x: ScalaVariableStructureViewElement => !x.isInherited
      case x: ScalaTypeAliasStructureViewElement => !x.isInherited
      case _ => super.isVisible(treeNode)
    }
  }
}