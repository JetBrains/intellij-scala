package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.{PsiElement, PsiFileFactory}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.project.ProjectContext

/*
  We could probably replace ScalaPsiElementFactory with this class:
  * It can create arbitrary code uniformly (instead of multiple create* methods)
  * In principle, it can cache & copy parsing results to avoid text re-parsing
  * It supports expression formatting: format("1 + %e", expr)
  * It provides string interpolation: code"1 + $expr"
  * Injected elements are inserted directly (without re-parsing)

  Arguments are handled like the following:
  * String is re-parsed
  * Int is converted to string and then re-parsed
  * Option[String], Option[Int] are re-parsed, if defined
  * PsiElement is inserted dirctly, without re-parsing
  * Option[PsiElement] is inserted directly, if defined
  * Seq[PsiElement] replaces parent's children elements
  * @@(Seq[PsiElement], separator = ", ") elements are inserted directly, separators are re-parsed
*/
object ScalaCode {
  private val FileName = "factory" + ScalaFileType.INSTANCE.getDefaultExtension
  private val Placeholder = "placeholder$0"

  // "unquote-splicing"
  case class @@(es: Seq[PsiElement], separator: String = ", ")

  private def parse(s: String)(implicit project: ProjectContext): ScalaFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText(FileName, ScalaFileType.INSTANCE, s).asInstanceOf[ScalaFile]

  private def format(format: String, elements: Any*)(implicit project: ProjectContext): ScalaPsiElement = {
    val file = parse(format.replace("%e", Placeholder))

    // we can optionally supplement element types to disambiguate target elements
    val placeholders = file.depthFirst(!_.textMatches(Placeholder)).filter(_.textMatches(Placeholder)).toVector

    if (placeholders.length != elements.length) {
      throw new IllegalArgumentException("Format string / arguments mismatch: %s VS %s".format(
        format, elements.map(_.getClass.getSimpleName).mkString(", ")))
    }

    placeholders.zip(elements).foreach {
      case (placeholder, element) => element match {
        case e: PsiElement =>
          placeholder.replace(e)
        case Seq(es @ _*) =>
          // simplified implementation of "unquote-splicing" (via replacing the parent element)
          placeholder.getParent.replace(es.head.asInstanceOf[PsiElement].getParent)
      }
    }

    file.getFirstChild match {
      case e: ScalaPsiElement => e
      case _ => throw new IllegalArgumentException("Cannot parse format: " + format)
    }
  }

  implicit class ScalaCodeContext(delegate: StringContext)(implicit project: ProjectContext) {
    def code(args: Any*)(implicit context: Context): ScalaPsiElement =
      context.select(code0(context.format(delegate.parts.head) +: delegate.parts.tail, args))

    private def code0(parts: Seq[String], args: Seq[Any]): ScalaPsiElement = {
      val separators = args.flatMap {
        case s: String => Seq(s)
        case i: Int => Seq(i.toString)
        case Some(s: String) => Seq(s)
        case Some(i: Int) => Seq(i.toString)
        case Some(_: PsiElement) => Seq("%e")
        case None => Seq.empty
        case @@(es, s) => Seq(Seq.fill(es.length)("%e").mkString(s))
        case _ => Seq("%e")
      }

      val argumetns = args.flatMap {
        case _: String | _: Int | None => Seq.empty
        case Some(_: String) => Seq.empty
        case Some(e: PsiElement) => Seq(e)
        case @@(es, _) => es
        case it => Seq(it)
      }

      val s = interleave(parts, separators).mkString

      format(s, argumetns: _*)
    }
  }

  private def interleave[T](xs: Seq[T], ys: Seq[T]): Seq[T] = (xs, ys) match {
    case (Seq(x, tx @ _*), Seq(y, ty @ _*)) => Seq(x, y) ++ interleave(tx, ty)
    case (Seq(x, tx @ _*), Seq()) => x +: interleave(tx, Seq.empty)
    case (Seq(), Seq(y, ty @ _*)) => y +: interleave(Seq.empty, ty)
    case (Seq(), Seq()) => Seq.empty
  }

  class Context(val format: String => String, val select: ScalaPsiElement => ScalaPsiElement)

  implicit val Block: Context = new Context(identity, identity)

  val Type = new Context("val v: " + _, _.getLastChild.asInstanceOf[ScTypeElement])
}
