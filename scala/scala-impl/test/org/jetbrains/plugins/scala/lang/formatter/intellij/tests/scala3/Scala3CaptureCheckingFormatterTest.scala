package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.project.{ScalaFeaturePusher, ScalaFeatures}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3CaptureCheckingFormatterTest extends Scala3FormatterBaseTest {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_8

  override protected def initFile(fileName: String, text: String): PsiFile = {
    val file = super.initFile(fileName, text)
    ScalaFeaturePusher.setFeatures(file.getVirtualFile,
      ScalaFeatures.onlyByVersion(version)
        .copy(version, hasCaptureCheckingEnabled = true)
    )
    file
  }

  def test_capture_type(): Unit =
    doTextTest(
      """
        |x: A  ^
        |x: A  ^  { }
        |x: left  ^  right
        |x: left  ^  (right)
        |x: left  ^  1
        |x: left  ^  "literal"
        |x: (left  ^  )  ^  right
        |x: arg  ^  ->  ret
        |x: arg  ^  ?->  ret
        |""".stripMargin,
      """
        |x: A^
        |x: A^{}
        |x: left ^ right
        |x: left ^ (right)
        |x: left ^ 1
        |x: left ^ "literal"
        |x: (left^) ^ right
        |x: arg^ -> ret
        |x: arg^ ?-> ret
        |""".stripMargin
    )

  def test_pure_function(): Unit =
    doTextTest(
      """
        |x: A->B  ->  C
        |x: A?->B  ?-> C
        |x: A   ->  { a }  B
        |x: A  ?->  { a }  B
        |""".stripMargin,
      """
        |x: A -> B -> C
        |x: A ?-> B ?-> C
        |x: A ->{a} B
        |x: A ?->{a} B
        |""".stripMargin
    )

  def test_capture_set(): Unit =
    doTextTest(
      """
        |x: A^{  id  .  id  .  rd  }
        |x: A^{  id  .  this  .  this  .  super  .  id  }
        |x: A^{  super  [  x  .  y  ]  .  id  }
        |x: A^{  this  .  id  .  id  .  as  [  x  ]  .  rd  }
        |x: A^{  this  *  }
        |x: A^{  this  *  .  as  [  x  ]  }
        |x: A^{  this  *  .  rd  }
        |""".stripMargin,
      """
        |x: A^{id.id.rd}
        |x: A^{id.this.this.super.id}
        |x: A^{super[x.y].id}
        |x: A^{this.id.id.as[x].rd}
        |x: A^{this*}
        |x: A^{this*.as[x]}
        |x: A^{this*.rd}
        |""".stripMargin
    )
}
