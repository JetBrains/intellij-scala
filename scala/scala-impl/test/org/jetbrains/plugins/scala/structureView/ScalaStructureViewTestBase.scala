package org.jetbrains.plugins.scala.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.ElementBase
import com.intellij.ui.LayeredIcon
import com.intellij.util.PlatformIcons
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.structureView.ScalaStructureViewModel
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node
import org.junit.Assert

import java.util.Comparator
import javax.swing.Icon
import scala.util.matching.Regex

abstract class ScalaStructureViewTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  protected val FinalMark = AllIcons.Nodes.FinalMark

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)

  protected def scalaLanguage: com.intellij.lang.Language

  protected def check(@Language("Scala") code: String, nodes: Node*): Unit = {
    val actualNode = {
      val file = psiFileOf(code)(getProject)

      val model = new ScalaStructureViewModel(file)

      val sorter: Seq[TreeElement] => Seq[TreeElement] = elements => {
        val comparators = model.getSorters.filterNot(_.isVisible).reverseIterator.map(_.getComparator.asInstanceOf[Comparator[TreeElement]])
        comparators.foldLeft(elements)((elements, comparator) => elements.sortWith((e1, e2) => comparator.compare(e1, e2) <= 0))
      }

      Node(model.getRoot, sorter)
    }

    val expected = new Node(ScalaFileType.INSTANCE.getIcon, "foo.scala", nodes: _*).toString

    Assert.assertEquals(expected, actualNode.toString)
  }

  protected def psiFileOf(@Language("Scala") text: String)(project: Project): ScalaFile = {
    PsiFileFactory.getInstance(project)
      .createFileFromText("foo.scala", scalaLanguage, text: CharSequence)
      .asInstanceOf[ScalaFile]
  }

  protected def layered(icons: Icon*): Icon = {
    val result = new LayeredIcon(icons.length)
    icons.zipWithIndex.foreach { case (icon, index) => result.setIcon(icon, index)}
    result
  }
}

private object ScalaStructureViewTestBase {
  private final val IconFileName = new Regex("(?<=/)[^/]+(?=\\.png)")

  class Node(icon: Icon, name: String, children: Node*) {
    override def toString: String =
      IconFileName.findAllIn(Option(icon).mkString).mkString("[", ", ", "] ") + name + "\n" +
        children.map(node => "  " + node.toString).mkString
  }

  object Node {
    def apply(baseIcon: Icon, visibilityIcon: Icon, name: String, children: Node*): Node =
      new Node(ElementBase.buildRowIcon(baseIcon, visibilityIcon), name, children: _*)

    def apply(icon: Icon, name: String, children: Node*): Node =
      Node(icon, PlatformIcons.PUBLIC_ICON, name, children: _*)

    def apply(element: StructureViewTreeElement, sorter: Seq[TreeElement] => Seq[TreeElement]): Node = {
      val presentation = element.getPresentation
      val children = sorter(element.getChildren.toSeq).map { case element: StructureViewTreeElement => Node(element, sorter) }
      new Node(presentation.getIcon(false), presentation.getPresentableText, children: _*)
    }
  }
}

