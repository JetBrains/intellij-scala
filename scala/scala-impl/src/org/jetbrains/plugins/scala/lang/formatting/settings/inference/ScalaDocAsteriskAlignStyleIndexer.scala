package org.jetbrains.plugins.scala.lang.formatting.settings.inference

import java.io.{DataInput, DataOutput}
import java.util.regex.Pattern

import com.intellij.application.options.CodeStyle
import com.intellij.util.indexing._
import com.intellij.util.io.{IntInlineKeyDescriptor, KeyDescriptor}
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.formatting.settings.inference.ScalaDocAsteriskAlignStyleIndexer.AsteriskAlignStyle.{AlignByColumnThree, AlignByColumnTwo}
import org.jetbrains.plugins.scala.lang.formatting.settings.inference.ScalaDocAsteriskAlignStyleIndexer.{AsteriskAlignStyle, _}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

// NOTE: low-level imperative style is intentionally used to avoid unnecessary allocations
// and not to explode indexing time for such a tiny feature
final class ScalaDocAsteriskAlignStyleIndexer extends FileBasedIndexExtension[AsteriskAlignStyle, Integer] {
  override val getName: ID[AsteriskAlignStyle, Integer] = ScalaDocAsteriskAlignStyleIndexer.Id

  override def getInputFilter: FileBasedIndex.InputFilter = file => {
    file.isInLocalFileSystem &&
      file.getFileType.isInstanceOf[ScalaFileType]
  }

  override def getIndexer: DataIndexer[AsteriskAlignStyle, Integer, FileContent] = (inputData: FileContent) => {
    val project = inputData.getProject

    val fileText = inputData.getContentAsText
    val fileTextLength = fileText.length

    var alignsByColumnTwo: Integer = 0
    var alignsByColumnThree: Integer = 0

    val tabSize = CodeStyle.getSettings(project).getTabSize(ScalaFileType.INSTANCE)
    val leadAsteriskMatcher = ScalaDocLeadAsteriskPattern.matcher(fileText)

    ScalaDocStartRegex.findAllMatchIn(fileText).foreach { regMatch =>
      val nextNewLineIdx = StringUtils.indexOf(fileText, '\n', regMatch.end)
      if (nextNewLineIdx > 0) {
        leadAsteriskMatcher.region(nextNewLineIdx, fileTextLength)
        if (leadAsteriskMatcher.lookingAt) {
          val indentSizeDocStart = indentSize(fileText, regMatch.start + 1, tabSize)
          val indentSizeNextLine = indentSize(fileText, nextNewLineIdx + 1, tabSize)
          indentSizeNextLine - indentSizeDocStart match {
            case 1 => alignsByColumnTwo += 1
            case 2 => alignsByColumnThree += 1
            case _ =>
          }
        }
      }
    }

    Map(
      (AsteriskAlignStyle.AlignByColumnTwo, alignsByColumnTwo),
      (AsteriskAlignStyle.AlignByColumnThree, alignsByColumnThree)
    ).asJava
  }

  @tailrec
  private def indentSize(chars: CharSequence, startIdx: Int, tabSize: Int, indentAcc: Int = 0): Int = {
    if (startIdx >= chars.length) {
      indentAcc
    } else chars.charAt(startIdx) match {
      case '\t' => indentSize(chars, startIdx + 1, tabSize, indentAcc + tabSize)
      case ' ' => indentSize(chars, startIdx + 1, tabSize, indentAcc + 1)
      case _ => indentAcc
    }
  }

  override def getKeyDescriptor: KeyDescriptor[AsteriskAlignStyle] = new KeyDescriptor[AsteriskAlignStyle] {
    override def getHashCode(value: AsteriskAlignStyle): Int = value.hashCode()
    override def isEqual(val1: AsteriskAlignStyle, val2: AsteriskAlignStyle): Boolean = val1 == val2
    override def save(out: DataOutput, value: AsteriskAlignStyle): Unit = out.writeInt(value.id)
    override def read(in: DataInput): AsteriskAlignStyle = in.readInt() match {
      case AsteriskAlignStyle.AlignByColumnTwo.id => AlignByColumnTwo
      case AsteriskAlignStyle.AlignByColumnThree.id => AlignByColumnThree
      case unknown => throw new RuntimeException(s"unknown scaladoc asterisk align id value: $unknown")
    }
  }

  override def getValueExternalizer: IntInlineKeyDescriptor = new IntInlineKeyDescriptor {
    override def isCompactFormat = true
  }

  override def dependsOnFileContent() = true

  override def getVersion = 0
}

object ScalaDocAsteriskAlignStyleIndexer {
  val Id: ID[AsteriskAlignStyle, Integer] = ID.create("ScalaDocAsteriskAlignStyleIndexer")

  private val ScalaDocStartRegex = """\n([\t ]+)/\*\*""".r
  private val ScalaDocLeadAsteriskPattern = Pattern.compile("""\n([\t ]+)\*""")

  /** @param id unique id used in KeyDescriptor */
  sealed abstract class AsteriskAlignStyle(val id: Int) extends Product with Serializable
  object AsteriskAlignStyle {
    case object AlignByColumnTwo extends AsteriskAlignStyle(0)
    case object AlignByColumnThree extends AsteriskAlignStyle(1)
  }
}

