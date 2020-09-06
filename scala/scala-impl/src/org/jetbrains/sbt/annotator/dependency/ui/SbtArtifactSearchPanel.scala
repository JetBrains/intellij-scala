package org.jetbrains.sbt.annotator.dependency.ui

import java.awt.BorderLayout
import javax.swing._
import javax.swing.event._

import com.intellij.icons.AllIcons
import com.intellij.util.text.VersionComparatorUtil.compare
import com.intellij.ui._
import com.intellij.ui.components.JBList
import org.jetbrains.sbt.annotator.dependency.ui.SbtArtifactSearchPanel._
import org.jetbrains.sbt.resolvers.ArtifactInfo

import scala.collection.JavaConverters.asJavaCollectionConverter

/**
  * Created by afonichkin on 7/13/17.
  */
private class SbtArtifactSearchPanel(wizard: SbtArtifactSearchWizard, artifactInfoSet: Set[ArtifactInfo]) extends JPanel {
  val myResultList = new JBList[ArtifactInfo]()

  init()

  def init(): Unit = {
    val artifacts = artifactInfoSet
      .toSeq
      .sortWith((a, b) =>
        a.groupId >= b.groupId &&
        a.artifactId >= b.artifactId &&
        compare(a.version, b.version) >= 0
      )

    myResultList.setModel(new DependencyListModel(artifacts))
    myResultList.getSelectionModel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    setLayout(new BorderLayout())

    val pane = ScrollPaneFactory.createScrollPane(myResultList)
    pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS) // Don't remove this line.
    add(pane, BorderLayout.CENTER)

    myResultList.setCellRenderer(new DependencyListCellRenderer())
    myResultList.addListSelectionListener((_: ListSelectionEvent) => wizard.updateButtons(false, !myResultList.isSelectionEmpty, true))
  }
}

private object SbtArtifactSearchPanel {
  class DependencyListModel(elems: Seq[ArtifactInfo]) extends CollectionListModel[ArtifactInfo](elems.asJavaCollection)

  class DependencyListCellRenderer extends ColoredListCellRenderer[ArtifactInfo] {
    //noinspection ScalaExtractStringToBundle,ReferencePassedToNls
    override def customizeCellRenderer(list: JList[_ <: ArtifactInfo], value: ArtifactInfo, index: Int, selected: Boolean, hasFocus: Boolean): Unit = {
      setIcon(AllIcons.Nodes.PpLib)
      append(s"${value.groupId}:", SimpleTextAttributes.GRAY_ATTRIBUTES)
      append(value.artifactId)
      append(s":${value.version}", SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }
}
