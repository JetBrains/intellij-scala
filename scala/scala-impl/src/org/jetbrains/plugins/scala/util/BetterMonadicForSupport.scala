package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * See: https://github.com/oleg-py/better-monadic-for
 */
class BetterMonadicForSupport(project: Project) {
  private[this] val implicit0: PsiElement = {
    val text =
    """
      |object implicit0 {
      | def unapply[A](a: A): Option[A] = ???
      |}
    """.stripMargin

    ScalaPsiElementFactory.createElement(text)(TmplDef()(_))(project)
  }

  val syntheticDeclarations: Seq[PsiElement] = Seq(
    implicit0
  )
}

object BetterMonadicForSupport {
  def apply(project: Project): BetterMonadicForSupport =
    project.getService(classOf[BetterMonadicForSupport])

  object Implicit0Pattern {
    private[this] def resolvesToImplicit0Unapply(ref: ScStableCodeReference): Boolean =
      (for {
        r    <- ref.bind()
        extr <- r.element.asOptionOf[ScFunction]
        if extr.isUnapplyMethod
      } yield extr.containingClass.name == "implicit0").getOrElse(false)

    def unapply(pat: ScConstructorPattern): Option[ScPattern] =
      if (pat.betterMonadicForEnabled) {
        pat match {
          case ScConstructorPattern(ref, ScPatternArgumentList(arg))
            if resolvesToImplicit0Unapply(ref) => arg.toOption
          case _ => None
        }
      } else None
  }

  /**
   * Checks that given expression is a binding inside a valid `implicit0` call (i.e. it resolves to
   * the correct synthetic method, has exaclty one argument, which is type ascripted
   * term name). Returns synthetic implicit value definition to be used for implicit resolution.
   */
  object Implicit0Binding {
    def unapply(e: ScTypedPattern): Boolean =
      if (e.betterMonadicForEnabled) {
        e match {
          case ScTypedPattern(_) && Parent(Parent(Implicit0Pattern(_))) => true
          case _                                                        => false
        }
      } else false
  }
}
