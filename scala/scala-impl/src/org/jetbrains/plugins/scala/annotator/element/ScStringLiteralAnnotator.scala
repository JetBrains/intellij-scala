package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}

object ScStringLiteralAnnotator extends ElementAnnotator[ScLiteral] {

  private val StringLiteralSizeLimit = 65536
  private val StringCharactersCountLimit = StringLiteralSizeLimit / 4

  override def annotate(literal: ScLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = literal match {
    case interpolated: ScInterpolatedStringLiteral =>
      createStringIsTooLongAnnotation(interpolated, interpolated.getStringParts: _*)
    case ScStringLiteral(string) => createStringIsTooLongAnnotation(literal, string)
    case _ =>
  }

  private def createStringIsTooLongAnnotation(literal: ScLiteral, strings: String*)
                                             (implicit holder: ScalaAnnotationHolder) = {
    import extensions.PsiElementExt
    implicit val virtualFile: Option[VirtualFile] = literal.containingVirtualFile

    if (strings.exists(exceedsLimit)) {
      holder.createErrorAnnotation(
        literal,
        ScalaBundle.message("string.literal.is.too.long")
      )
    }
  }

  private def exceedsLimit(string: String)
                          (implicit virtualFile: Option[VirtualFile]): Boolean = string.length match {
    case length if length >= StringLiteralSizeLimit => true
    case length if length >= StringCharactersCountLimit => utf8Size(string) >= StringLiteralSizeLimit
    case _ => false
  }

  private def utf8Size(string: String)
                      (implicit virtualFile: Option[VirtualFile]): Int = {
    val lineSeparator = virtualFile
      .flatMap(virtualFile => Option(virtualFile.getDetectedLineSeparator))
      .getOrElse(Option(System.lineSeparator).getOrElse("\n"))

    string.map {
      case '\n' => lineSeparator.length
      case '\r' => 0
      case character if character >= 0 && character <= '\u007F' => 1
      case character if character >= '\u0080' && character <= '\u07FF' => 2
      case character if character >= '\u0800' && character <= '\uFFFF' => 3
      case _ => 4
    }.sum
  }
}
