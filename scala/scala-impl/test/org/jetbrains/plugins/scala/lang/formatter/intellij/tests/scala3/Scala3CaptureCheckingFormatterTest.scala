package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.base.CaptureCheckingTestUtils
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class Scala3CaptureCheckingFormatterTest extends Scala3FormatterBaseTest {
  override protected def version: ScalaVersion = LatestScalaVersions.Scala_3_8

  override protected def setUp(): Unit = {
    super.setUp()
    CaptureCheckingTestUtils.enableCaptureCheckingInLightModule(getModule, version, getTestRootDisposable)
  }

  /**
   * Creates a physical file in the configured module so committed formatting edits
   * use its Scala version and capture-checking option. Pushing features onto a light
   * virtual file alone does not configure the module features used during reparsing.
   */
  override protected def initFile(fileName: String, text: String): PsiFile = inWriteAction {
    val root = ModuleRootManager.getInstance(getModule).getSourceRoots.head
    val virtualFile = root.createChildData(this, fileName)
    VfsUtil.saveText(virtualFile, text)
    PsiManager.getInstance(getProject).findFile(virtualFile)
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
        |""".stripMargin,
      repeats = 2,
      checkAfterEachIteration = true
    )
}
