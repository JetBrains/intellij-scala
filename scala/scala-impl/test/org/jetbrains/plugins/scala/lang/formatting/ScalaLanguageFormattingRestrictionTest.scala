package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.PsiFileTestUtil.addFileToProject

class ScalaLanguageFormattingRestrictionTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_8

  protected override def includeScalaLibrarySources: Boolean = true

  private val exampleFiles: Map[String, Boolean] = Map(
    "xyz/Foo.scala" -> true,
    "xyz/Foo2.java" -> true,
    "!/scala/collection/concurrent/BasicNode.java" -> true,
    "!/scala/collection/immutable/package.scala" -> false,
    "!/scala/Tuple.scala" -> false,
    "!/scala/Predef.tasty" -> true,
    "!/java/lang/String.class" -> true,
  )


  def test_sources(): Unit = {
    setupProject()

    var missingFiles = exampleFiles
    val psiManager = PsiManager.getInstance(getProject)

    def processFile(file: VirtualFile): Unit = {
      val psiFile = psiManager.findFile(file)
      if (psiFile != null) {
        def autoFormattingAllowed = LanguageFormatting.INSTANCE.isAutoFormatAllowed(psiFile)
        val path = file.getPath
        exampleFiles.iterator
          .find { case (name, _) => path.contains(name) }
          .foreach {
            case (name, expectedAutoformatting) =>
              missingFiles -= name
              assert(
                autoFormattingAllowed == expectedAutoformatting,
                s"File $path should have autoformatting allowed = $expectedAutoformatting"
              )
          }
      }
    }

    def visitFile(file: VirtualFile): Unit = {
      if (file.isDirectory) {
        file.getChildren.foreach(visitFile)
      } else {
        processFile(file)
      }
    }

    val index = WorkspaceFileIndexEx.getInstance(getProject)
    index.visitFileSets { (a, _) =>
      visitFile(a.getRoot)
    }

    assert(missingFiles.isEmpty, s"Missing files: ${missingFiles.keys.mkString(", ")}")
  }

  private def setupProject(): Unit = {
    addFileToProject(
      "src/xyz/Foo.scala",
      """package xyz
        |
        |object Foo
        |""".stripMargin,
      getProject
    )

    addFileToProject(
      "src/xyz/Foo2.java",
      """
        |package xyz;
        |
        |public class Foo2 {
        |
        |}
        |""".stripMargin,
      getProject
    )
  }
}
