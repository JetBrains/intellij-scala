package org.jetbrains.plugins.scala.externalLibraries.bm4

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ScalaFeatures

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


    val file = ScalaPsiElementFactory.createScalaFileFromText(text, ScalaFeatures.default)(project)
    file.typeDefinitions.head
  }

  val syntheticDeclarations: Seq[PsiElement] = Seq(
    implicit0
  )
}

object BetterMonadicForSupport {
  def apply(project: Project): BetterMonadicForSupport =
    project.getService(classOf[BetterMonadicForSupport])
}
