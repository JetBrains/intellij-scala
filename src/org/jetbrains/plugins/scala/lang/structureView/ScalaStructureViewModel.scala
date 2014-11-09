package org.jetbrains.plugins.scala
package lang
package structureView

import java.util

import com.intellij.ide.structureView.{StructureViewModel, StructureViewTreeElement, TextEditorBasedStructureViewModel}
import com.intellij.ide.util.treeView.smartTree._
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.structureView.elements.impl._

/**
 * @author Alexander Podkhalyuzin
 * @since 04.05.2008
 */
class ScalaStructureViewModel(private val myRootElement: ScalaFile, private val console: ScalaLanguageConsole = null)
  extends TextEditorBasedStructureViewModel(myRootElement) with StructureViewModel.ElementInfoProvider {
  def isAlwaysLeaf(element: StructureViewTreeElement): Boolean = !isAlwaysShowsPlus(element)

  def isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = {
    element match {
      case _: ScalaTypeDefinitionStructureViewElement => true
      case _: ScalaFileStructureViewElement => true
      case _: ScalaPackagingStructureViewElement => true
      case _ => false
    }
  }

  @NotNull
  def getRoot: StructureViewTreeElement = {
    new ScalaFileStructureViewElement(myRootElement, console)
  }

  @NotNull
  override def getSorters: Array[Sorter] = {
    val res = new Array[Sorter](1)
    res(0) = Sorter.ALPHA_SORTER
    res
  }

  override def getNodeProviders: util.Collection[NodeProvider[_ <: TreeElement]] = ScalaStructureViewModel.NODE_PROVIDERS

  override def isSuitable(element: PsiElement): Boolean = element match {
    case t: ScTypeDefinition => t.getParent match {
      case _: ScalaFile | _: ScPackaging => true
      case tb: ScTemplateBody if tb.getParent.isInstanceOf[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case f: ScFunction => f.getParent match {
      case b: ScBlockExpr => b.getParent.isInstanceOf[ScFunction]
      case tb: ScTemplateBody if tb.getParent.isInstanceOf[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case m: ScMember => m.getParent match {
      case tb: ScTemplateBody if tb.getParent.isInstanceOf[ScExtendsBlock] =>
        isSuitable(tb.getParent.getParent)
      case _ => false
    }
    case _ => false
  }

  override def shouldEnterElement(o: Object): Boolean = o match {
    case t : ScTypeDefinition => t.members.length > 0 || t.typeDefinitions.size > 0
    case _ => false
  }
}

object ScalaStructureViewModel {
  private val NODE_PROVIDERS: util.Collection[NodeProvider[_ <: TreeElement]] =
    util.Arrays.asList(new ScalaInheritedMembersNodeProvider)
}