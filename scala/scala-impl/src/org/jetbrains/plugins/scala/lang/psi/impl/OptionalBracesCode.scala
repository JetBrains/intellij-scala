package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaFeatures}

/**
 * Can be used to reduce branching in places where there is a need to support both
 * Scala 2 syntax and Scala 3 indentation-based syntax.
 *
 * For example {{{
 *   optBraces"""class Foo$TemplateBodyStart
 *              |  val bar = 0$TemplateBodyEnd
 *              |""".stripMargin
 * }}}
 *
 * is translated to {{{
 *   """class Foo {
 *     |  val bar = 0
 *     |}""".stripMargin
 * }}}
 * or, if use indentation based syntax flag is on, to {{{
 *   """class Foo:
 *     |  val bar = 0""".stripMargin
 * }}}
 */
object OptionalBracesCode {
  sealed trait BlockEndLike

  case object TemplateBodyStart
  case object TemplateBodyEnd extends BlockEndLike
  case object BlockStart
  case object BlockEnd extends BlockEndLike

  implicit final class ScalaOptionalBracesCodeContext(delegate: StringContext)
                                                     (implicit ctx: Project, features: ScalaFeatures) {
    def optBraces(args0: Any*): String = {
      val parts = delegate.parts.iterator
      val args  = args0.iterator

      val isBraceless = ctx.indentationBasedSyntaxEnabled(features)

      val sb = new StringBuilder(parts.next())
      while (args.hasNext) {
        val next = args.next() match {
          case BlockStart         => if (isBraceless) "" else " {"
          case TemplateBodyStart  => if (isBraceless) ":" else " {"
          case _: BlockEndLike    => if (isBraceless) "" else "\n}"
          case x                  => x
        }
        sb.append(next)
        sb.append(parts.next())
      }
      sb.result()
    }
  }
}
