package debug

import com.intellij.formatting._
import intellijhocon.Util
import scala.collection.JavaConverters._

object Debug {

  import Util._

  def dumpBlock(block: Block) = {
    def dump(block: Block, spacing: Spacing): String = {
      val text = block match {
        case astBlock: ASTBlock if block.getSubBlocks.isEmpty =>
          "\"" + astBlock.getNode.getText + "\" "
        case _ => ""
      }

      val tpe = block match {
        case astBlock: ASTBlock =>
          astBlock.getNode.getElementType
        case _ => null
      }

      val attrs = List(
        dumpIndent(block.getIndent),
        dumpSpacing(spacing),
        dumpWrap(block.getWrap),
        dumpAlignment(block.getAlignment)
      ).filter(_.nonEmpty).mkString("", "\n", "\n")

      val main = s"$tpe ${block.getTextRange} $text"

      val subBlocks = block.getSubBlocks.asScala.toList
      val children =
        if (subBlocks.nonEmpty)
          ((null :: subBlocks) zip subBlocks).map {
            case (left, right) => dump(right, block.getSpacing(left, right))
          }.mkString("\n\n", "\n\n", "").indent("  ")
        else ""

      attrs + main + children
    }

    dump(block, null)
  }

  def callGetter(clazz: Class[_], name: String, obj: AnyRef) = {
    val getter = clazz.getDeclaredMethod(name)
    getter.setAccessible(true)
    getter.invoke(obj)
  }

  def getField(clazz: Class[_], name: String, obj: AnyRef) = {
    val field = clazz.getDeclaredField(name)
    field.setAccessible(true)
    field.get(obj)
  }

  def dumpSpacing(spacing: Spacing) =
    if (spacing != null) {
      val spacingImplClazz = Class.forName("com.intellij.formatting.SpacingImpl")

      val baseFields = List[(String, Any)](
        ("myMinSpaces", 0),
        ("myMaxSpaces", 0),
        ("myKeepBlankLines", 2),
        ("myMinLineFeeds", 0),
        ("myPrefLineFeeds", 0)
      ).flatMap { case (field, defaultValue) =>
        val value = getField(spacingImplClazz, field, spacing)
        if (value != defaultValue) Some(s"$field=$value") else None
      }

      val baseProps = List[(String, Any)](
        ("getMinSpaces", 0),
        ("getMaxSpaces", 0),
        ("getMinLineFeeds", 0),
        ("isReadOnly", false),
        ("containsLineFeeds", false),
        ("isSafe", false),
        ("shouldKeepLineFeeds", true),
        ("getKeepBlankLines", 2),
        ("shouldKeepFirstColumn", false),
        ("getPrefLineFeeds", 0)
      ).flatMap { case (getter, defaultValue) =>
        val value = callGetter(spacingImplClazz, getter, spacing)
        if (value != defaultValue) Some(s"$getter()=$value") else None
      }

      val dependentProps = spacing match {
        case _: DependantSpacingImpl =>
          List(
            "myDependency",
            "myRule"
          ).map { field =>
            s"$field=${getField(spacingImplClazz, field, spacing)}"
          }

        case _ => Nil
      }

      (baseFields ++ baseProps ++ dependentProps).mkString("SPACING: ", ", ", "")
    } else ""

  def dumpIndent(indent: Indent) = if (indent != null) {
    val indentImplClass = Class.forName("com.intellij.formatting.IndentImpl")

    List[(String, Any)](
      ("myIsAbsolute", false),
      ("myRelativeToDirectParent", false),
      ("myType", null),
      ("mySpaces", 0),
      ("myEnforceIndentToChildren", false)
    ).flatMap { case (field, defaultValue) =>
      val value = getField(indentImplClass, field, indent)
      if (value != defaultValue) Some(s"$field=$value") else None
    }.mkString("INDENT: ", ", ", " ")

  } else ""

  def dumpWrap(wrap: Wrap) = if (wrap != null) {
    val wrapImplClass = Class.forName("com.intellij.formatting.WrapImpl")

    (List[(String, Any)](
      ("getType", null),
      ("isWrapFirstElement", true)
    ).flatMap { case (getter, defaultValue) =>
      val value = callGetter(wrapImplClass, getter, wrap)
      if (value != defaultValue) Some(s"$getter()=$value") else None
    } :+ s"hash=${System.identityHashCode(wrap)}").mkString("WRAP: ", ", ", "")
  } else ""

  def dumpAlignment(alignment: Alignment): String = if (alignment != null) {
    val alignmentImplClass = Class.forName("com.intellij.formatting.AlignmentImpl")

    val parentAlignment = dumpAlignment(getField(alignmentImplClass, "myParentAlignment", alignment).asInstanceOf[Alignment])
    (List(
      ("myAllowBackwardShift", false),
      ("myAnchor", null)
    ).flatMap { case (field, defaultValue) =>
      val value = getField(alignmentImplClass, field, alignment)
      if (value != defaultValue) Some(s"$field=$value") else None
    } :+ s"hash=${System.identityHashCode(alignment)}" :+ s"parent=$parentAlignment")
      .mkString("ALIGNMENT: ", ", ", "")

  } else ""

}
