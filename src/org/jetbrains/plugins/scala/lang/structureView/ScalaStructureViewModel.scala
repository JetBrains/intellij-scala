package org.jetbrains.plugins.scala
package lang
package structureView

import com.intellij.ide.util.treeView.smartTree._
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.structureView.elements.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import psi.api.ScalaFile
import com.intellij.ide.structureView.{StructureViewModel, StructureViewTreeElement, TextEditorBasedStructureViewModel}
import java.util.{Arrays, Collection}
import console.ScalaLanguageConsole

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

  override def getNodeProviders: Collection[NodeProvider[_ <: TreeElement]] = ScalaStructureViewModel.NODE_PROVIDERS

  override def isSuitable(element: PsiElement) = element match {
    case t: ScTypeDefinition => t.getParent match {
      case _: ScalaFile | _: ScPackaging => true
      case tb: ScTemplateBody if tb.getParent.isInstanceOf[ScExtendsBlock] => {
        isSuitable(tb.getParent.getParent)
      }
      case _ => false
    }
    case f: ScFunction => f.getParent match {
      case b: ScBlockExpr => b.getParent.isInstanceOf[ScFunction]
      case tb: ScTemplateBody if (tb.getParent.isInstanceOf[ScExtendsBlock]) => {
        isSuitable(tb.getParent.getParent)
      }
      case _ => false
    }
    case m: ScMember => m.getParent match {
      case tb: ScTemplateBody if (tb.getParent.isInstanceOf[ScExtendsBlock]) => {
        isSuitable(tb.getParent.getParent)
      }
      case _ => false
    }
    case _ => false
  }

  override def shouldEnterElement(o: Object) = o match {
    case t : ScTypeDefinition => t.members.length > 0 || t.typeDefinitions.size > 0
    case _ => false
  }
}

object ScalaStructureViewModel {
  private val NODE_PROVIDERS: Collection[NodeProvider[_ <: TreeElement]] = Arrays.asList(new ScalaInheritedMembersNodeProvider)
}