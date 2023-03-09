package org.jetbrains.plugins.scala.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.ElementBase
import com.intellij.ui.{CoreIconManager, IconManager, LayeredIcon, PlatformIcons}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.structureView.ScalaStructureViewBuilder
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewTestBase.Node
import org.junit.Assert
import org.junit.Assert.fail

import java.util.Comparator
import javax.swing.Icon
import scala.util.matching.Regex

abstract class ScalaStructureViewTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected val FinalMark = AllIcons.Nodes.FinalMark
  protected val Private = AllIcons.Nodes.Private

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)

  protected def scalaLanguage: com.intellij.lang.Language

  override def isIconRequired: Boolean = true

  override def setUp(): Unit = {
    super.setUp()

    /**
     * By default IconManager is deactivated and `com.intellij.ui.DummyIconManager` is used
     * We need a proper IconManager implementation, in order layered icons are properly built in structure view tests.
     * (see [[org.jetbrains.plugins.scala.util.BaseIconProvider.getIcon]])
     */
    IconManager.getInstance() match {
      case iconManager: CoreIconManager =>
        // workaround for IDEA-274148 (can remove it when the issue is fixed)
        // copied from com.intellij.psi.impl.ElementPresentationUtil static initializer
        val FLAGS_STATIC = 0x200
        val FLAGS_FINAL = 0x400
        val FLAGS_JUNIT_TEST = 0x2000
        val FLAGS_RUNNABLE = 0x4000

        iconManager.registerIconLayer(FLAGS_STATIC, AllIcons.Nodes.StaticMark)
        iconManager.registerIconLayer(FLAGS_FINAL, AllIcons.Nodes.FinalMark)
        iconManager.registerIconLayer(FLAGS_JUNIT_TEST, AllIcons.Nodes.JunitTestMark)
        iconManager.registerIconLayer(FLAGS_RUNNABLE, AllIcons.Nodes.RunnableMark)
      case m =>
        fail(s"Unexpected icon manager: ${m.getClass} (expected ${classOf[CoreIconManager]})")
    }
  }

  protected def check(@Language("Scala") code: String, nodes: Node*): Unit = {
    val structureView = createScalaStructureView(code)
    val model = structureView.getTreeModel

    val sorter: Seq[TreeElement] => Seq[TreeElement] = elements => {
      val comparators = model.getSorters.filterNot(_.isVisible).reverseIterator.map(_.getComparator.asInstanceOf[Comparator[TreeElement]])
      comparators.foldLeft(elements)((elements, comparator) => elements.sortWith((e1, e2) => comparator.compare(e1, e2) <= 0))
    }

    val actualNode: Node = Node(model.getRoot, sorter)

    val expectedNode = new Node(ScalaFileType.INSTANCE.getIcon, "foo.scala", nodes: _*)

    val expectedStr = expectedNode.presentation()
    val actualStr   = actualNode.presentation()
    Assert.assertEquals(expectedStr, actualStr)
  }

  protected def createScalaStructureView(code: String): StructureViewComponent = {
    val file = createScalaFile(code)(getProject)

    val builder = new ScalaStructureViewBuilder(file)
    val component = builder.createStructureView(null /*unused editor*/ , getProject).asInstanceOf[StructureViewComponent]
    Disposer.register(getTestRootDisposable, component) //to avoid "Thread Leaked" exceptions
    component
  }

  protected def check(structureView: StructureViewComponent, expectedNodes: Node*): Unit = {
    val model = structureView.getTreeModel

    val sorter: Seq[TreeElement] => Seq[TreeElement] = elements => {
      val comparators = model.getSorters.filterNot(_.isVisible).reverseIterator.map(_.getComparator.asInstanceOf[Comparator[TreeElement]])
      comparators.foldLeft(elements)((elements, comparator) => elements.sortWith((e1, e2) => comparator.compare(e1, e2) <= 0))
    }
    val rootNode = model.getRoot
    val actualNode: Node = Node(rootNode, sorter)

    val expectedNode = new Node(ScalaFileType.INSTANCE.getIcon, "foo.scala", expectedNodes: _*)

    val expectedStr = expectedNode.presentation()
    val actualStr   = actualNode.presentation()
    Assert.assertEquals(expectedStr, actualStr)
  }

  protected def createScalaFile(@Language("Scala") text: String)(project: Project): ScalaFile = {
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
  // Example of Icon.toString:
  // Row icon. myIcons=[Layered icon 16x16. myIcons=[UrlResolver{ownerClass=org.jetbrains.plugins.scala.icons.Icons, classLoader=jdk.internal.loader.ClassLoaders$AppClassLoader@61064425, overriddenPath='/org/jetbrains/plugins/scala/images/class_scala.svg', url=file:/unresolved, useCacheOnLoad=true}, nodes/finalMark.svg], nodes/c_public.svg]
  // we need just: [class_scala, finalMark, c_public]
  private final val IconFileName = new Regex("(?<=/)[^/]+(?=\\.\\w+)") // detect any extension

  class Node(icon: Icon, name: String, children: Node*) {
    def presentation(deep: Int = 0): String = {
      val iconString = IconFileName.findAllIn(Option(icon).mkString).mkString("[", ", ", "] ")
      val childIndent = "  " * deep
      val childrenString = children.map(_.presentation(deep + 1)).map(childIndent + _)
      iconString + (name +: childrenString).mkString("\n")
    }
  }

  object Node {
    def apply(baseIcon: Icon, visibilityIcon: Icon, name: String, children: Node*): Node =
      new Node(ElementBase.buildRowIcon(baseIcon, visibilityIcon), name, children: _*)

    def apply(icon: Icon, name: String, children: Node*): Node =
      Node(icon, IconManager.getInstance.getPlatformIcon(PlatformIcons.Public), name, children: _*)

    def apply(element: StructureViewTreeElement, sorter: Seq[TreeElement] => Seq[TreeElement]): Node = {
      val presentation = element.getPresentation
      val icon = presentation.getIcon(false)
      val text = presentation.getPresentableText
      val children = sorter(element.getChildren.toSeq).map { case element: StructureViewTreeElement => Node(element, sorter) }
      new Node(icon, text, children: _*)
    }
  }
}
