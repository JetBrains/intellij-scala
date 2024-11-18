package org.jetbrains.plugins.scala.inferAst

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PathExt, PsiElementExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Paths

class InferAstTest extends ScalaLightCodeInsightFixtureTestCase {
  def testInferRun(): Unit = {
    val testDataDir = Paths.get(TestUtils.getTestDataPath)
    val scalaImplPath = TestUtils.findCommunityRootPath / "scala/scala-impl"
    val scalaImplSrcPath = scalaImplPath / "src"

    println("Prepare files...")

    def copyDirFromScalaImpl(p: String): VirtualFile = {
      val path = scalaImplSrcPath / p
      assert(path.startsWith(scalaImplSrcPath), s"$path is not in scala-impl/src")
      myFixture.copyDirectoryToProject(
        testDataDir.relativize(path).toString,
        scalaImplSrcPath.relativize(path).toString
      )
    }

    myFixture.copyDirectoryToProject("inferAst", "")

    val parserVF = copyDirFromScalaImpl("org/jetbrains/plugins/scala/lang/parser")

    val blubVFile = parserVF.findFileByRelativePath("parsing/Blub.scala")
    assert(blubVFile != null)
    val blubPsiFile = PsiManager.getInstance(getProject).findFile(blubVFile)



    println("Gather functions...")
    val allMethods =
      Seq(blubPsiFile).flatMap { file =>
        file.depthFirst(e => !e.is[ScFunctionDefinition])
          .collect { case m: ScFunctionDefinition => m }
          .flatMap(m => m.qualifiedNameOpt.map(_ -> m))
      }.toMap

    //allMethods.keys.toSeq.sorted.foreach(println)

    println("Run analysis...")
    val analysis = new GlobalAnalysis(getProject)
    analysis.addToAnalysis(allMethods("org.jetbrains.plugins.scala.lang.parser.parsing.Blub.parse"))

    analysis.run()
  }
}
