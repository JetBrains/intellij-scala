package org.jetbrains.plugins.scala.project

import com.intellij.ui.SimpleListCellRenderer
import org.jetbrains.annotations.Nullable

import javax.swing.JList

/**
 * @param default used as fallback text if value is null
 */
class NonNullableValueBasedListRenderer[T](
  valueTransformer: T => String,
  @Nullable default: String = null
) extends SimpleListCellRenderer[T] {

  override def customize(list: JList[_ <: T], value: T, index: Int, selected: Boolean, hasFocus: Boolean): Unit = {
    val text = if (value == null) default else valueTransformer(value)
    setText(text)
  }
}
