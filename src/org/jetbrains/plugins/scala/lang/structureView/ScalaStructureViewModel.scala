package org.jetbrains.plugins.scala
package lang
package structureView

import java.util
import java.util.Comparator

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.{StructureViewModel, StructureViewTreeElement, TextEditorBasedStructureViewModel}
import com.intellij.ide.util.treeView.smartTree._
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.console.ScalaLanguageConsole
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.structureView.elements.impl._
import org.jetbrains.plugins.scala.testingSupport.test.structureView.TestNodeProvider

/**
 * @author Alexander Podkhalyuzin
 * @since 04.05.2008
 */
class ScalaStructureViewModel(private val myRootElement: ScalaFile, private val console: ScalaLanguageConsole = null)
  extends TextEditorBasedStructureViewModel(myRootElement) with StructureViewModel.ElementInfoProvider {
  def isAlwaysLeaf(element: StructureViewTreeElement): Boolean = !(isAlwaysShowsPlus(element) ||
      element.isInstanceOf[TestStructureViewElement] || element.isInstanceOf[ScalaValueStructureViewElement])

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
    res(0) = new Sorter() {
      override def isVisible: Boolean = true

      override def getComparator: Comparator[_] = new Comparator[AnyRef] {
        override def compare(o1: AnyRef, o2: AnyRef): Int =
          (o1, o2) match {
            case (_: TestStructureViewElement, _: TestStructureViewElement) => 0
            case (_, _: TestStructureViewElement) => -1
            case (_: TestStructureViewElement, _) => 1
            case _ => SorterUtil.getStringPresentation(o1).compareToIgnoreCase(SorterUtil.getStringPresentation(o2))
          }
      }

      override def getName: String = "ALPHA_SORTER_IGNORING_TEST_NODES"

      override def getPresentation: ActionPresentation = new ActionPresentationData(IdeBundle.message("action.sort" +
          ".alphabetically"), IdeBundle.message("action.sort.alphabetically"), AllIcons.ObjectBrowser.Sorted)
    }
    res
  }

  override def getNodeProviders: util.Collection[NodeProvider[_ <: TreeElement]] = {
    if (myRootElement.getFileType == ScalaFileType.SCALA_FILE_TYPE)
      util.Arrays.asList(new ScalaInheritedMembersNodeProvider, new TestNodeProvider)
    else
      util.Arrays.asList(new ScalaInheritedMembersNodeProvider)
  }

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
    case t : ScTypeDefinition => t.members.nonEmpty || t.typeDefinitions.nonEmpty
    case _ => false
  }
}