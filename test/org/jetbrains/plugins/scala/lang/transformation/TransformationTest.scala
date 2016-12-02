package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.psi.impl.DebugUtil.psiToString
import com.intellij.psi.{PsiElement, PsiFileFactory}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.LibraryTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert

/**
  * @author Pavel Fatin
  */
abstract class TransformationTest(transformation: PsiElement => Unit, @Language("Scala") defaultHeader: String = "") extends LibraryTestCase {
  private val PredefinedHeader =
    """
      class A { def a(): Unit = _ }
      class B { def b(): Unit = _ }
      class C { def c(): Unit = _ }
      object A extends A
      object B extends B
      object C extends C
    """

  protected def check(@Language("Scala") before: String, @Language("Scala") after: String) {
    check("", before, after)
  }

  protected def check(@Language("Scala") header: String,
                      @Language("Scala") before: String,
                      @Language("Scala") after: String,
                      @Language("Scala") footer: String = "") {

    val prefix = Seq(PredefinedHeader, clean(defaultHeader), clean(header))
      .filterNot(_.isEmpty).mkString("",  "\n", "\n")

    val suffix = "\n" + clean(footer)

    val file = parse(prefix + before + suffix)

    file.depthFirst().foreach(transformation)

    Assert.assertEquals(after, file.getText.substring(prefix.length, file.getText.length - suffix.length))
    Assert.assertEquals(psiToString(parse(prefix + after + suffix), false), psiToString(file, false))
  }

  private def clean(s: String) = s.split("\r?\n").toSeq.map(_.trim).filterNot(_.isEmpty).mkString("\n")

  private def parse(@Language("Scala") s: String): ScalaFile = {
    PsiFileFactory.getInstance(myFixture.getProject)
      .createFileFromText("foo.scala", ScalaFileType.INSTANCE, s)
      .asInstanceOf[ScalaFile]
  }
}
