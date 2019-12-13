package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

abstract class VarianceTestBase extends SimpleTestCase {
  final val Header = "class A; class B\n"

  protected def annotateFun(element: PsiElement, annotator: ScalaAnnotator, mock: AnnotatorHolderMock): Unit = {
    implicit val implicitMock: AnnotatorHolderMock = mock
    element match {
      case fun: ScFunction        => annotator.annotate(fun)
      case varr: ScVariable       => annotator.annotate(varr)
      case v: ScValue             => annotator.annotate(v)
      case tbo: ScTypeBoundsOwner => annotator.annotate(tbo)
      case call: ScMethodCall     => annotator.annotate(call)
      case td: ScTypeDefinition   => annotator.annotate(td)
      case td: ScClassParameter   => annotator.annotate(td)
      case _                      =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = ScalaAnnotator.forProject
    val file: ScalaFile = (Header + code).parse
    val mock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach {annotateFun(_, annotator, mock)}

    mock.annotations.filter((p: Message) => !p.isInstanceOf[Info])
  }

  val ContravariantPosition = ContainsPattern("occurs in contravariant position")
  val CovariantPosition = ContainsPattern("occurs in covariant position")
  val AbstractModifier = ContainsPattern("Abstract member may not have private modifier")
  val NotConformsUpper = ContainsPattern("doesn't conform to upper bound")
}
