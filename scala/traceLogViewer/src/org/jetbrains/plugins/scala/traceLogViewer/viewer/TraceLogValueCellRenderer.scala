package org.jetbrains.plugins.scala.traceLogViewer.viewer

import java.awt.event.MouseEvent
import scala.util.Random

class TraceLogValueCellRenderer extends TraceLogBaseCellRenderer {
  private def anyToHexColor(obj: Any): String = {
    val random = new Random(obj.hashCode())
    s"#${(random.nextInt() % (1 << 24)).toHexString}"
  }

  private def wrapHtmlColor(text: String): String = {
    val color = anyToHexColor(text)
    s"""<font color="$color">$text</font>"""
  }

  override def setup(): Unit = {
    setValue(
      currentNode.values
        .filterNot { case (name, _) => name == "this" }
        .map { case (name, data) => s"${wrapHtmlColor(name)}: $data" }
        .mkString("<html>", ", ", "</html>")
    )
  }

  override def getToolTipText(event: MouseEvent): String = {
    if (currentNode.values.isEmpty) {
      return null
    }

    val builder = new StringBuilder

    builder.append("<html><table>")

    for ((name, value) <- currentNode.values) {
      builder.append("<tr style=\"vertical-align:top>")
      builder.append(s"""<td><b style="${anyToHexColor(name)}">""")
      builder.append(wrapHtmlColor(name))
      builder.append(":</b> </td>")
      builder.append("<td> ")
      builder.append(value)
      builder.append("</td>")
      builder.append("</tr>")
    }

    builder.append("</table></html>")

    builder.toString()
  }
}
