package org.jetbrains.plugins.scala.structureView.sorter

import com.intellij.ide.structureView.impl.java.VisibilitySorter
import org.jetbrains.annotations.NonNls

object ScalaVisibilitySorter extends VisibilitySorter {
  @NonNls val ID = "SCALA_VISIBILITY_SORTER"

  override val getName: String = ScalaVisibilitySorter.ID
}
