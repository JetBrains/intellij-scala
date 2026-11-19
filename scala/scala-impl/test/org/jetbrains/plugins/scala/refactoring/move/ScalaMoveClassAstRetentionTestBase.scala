package org.jetbrains.plugins.scala.refactoring.move

import com.intellij.lang.FileASTNode
import com.intellij.psi.PsiClass
import com.intellij.util.ref.GCUtil
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.refactoring.Associations
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.junit.Assert._

import java.lang.ref.{SoftReference, WeakReference}
import java.nio.file.Path

/**
 * Runs the existing SCL-4875 fixture with GC after association collection.
 * The shared Move test harness holds no protective AST references, so these checks
 * exercise production retention.
 */
abstract class ScalaMoveClassAstRetentionTestBase extends ScalaMoveClassTestBase {

  override protected def getTestDataDirectory: Path =
    super.getTestDataRoot / "scala2_common" / "scl4875"

  def testAssociationsSurviveGc(): Unit = {
    var checked = false
    registerBeforeMoveAction { movingClass =>
      checkAstSurvivesGc(movingClass)
      checked = true
    }

    doTest(Seq("com.A"), "org")

    assertTrue("The GC check did not run", checked)
  }

  def testExceptionalExit(): Unit = {
    var originalTree: WeakReference[FileASTNode] = null
    val failure = new RuntimeException("Stop the move after collecting associations")
    registerBeforeMoveAction { movingClass =>
      originalTree = checkAstSurvivesGc(movingClass)
      throw failure
    }

    // The fixture uses moveCompanion = true; start with false to check restoration after failure.
    withMoveCompanionSetting(moveCompanion = false) {
      val thrown = assertThrows(classOf[RuntimeException], () => doMove(Seq("com.A"), "org"))
      assertSame(failure, thrown)
      assertFalse("The failed move must restore test settings", ScalaApplicationSettings.getInstance().MOVE_COMPANION)
      assertNull("The failed move should not create a target file", getRootBefore.findFileByRelativePath("org/A.scala"))
      forceGc()
      assertTrue("The failed move must release the original AST", originalTree.refersTo(null))
    }
  }

  private def checkAstSurvivesGc(clazz: PsiClass): WeakReference[FileASTNode] = {
    val originalTree = weakTree(clazz)
    assertNotNull("The move must have collected associations before GC", Associations.Data(clazz))
    forceGc()
    assertFalse("The original file AST was collected before restoring associations", originalTree.refersTo(null))
    assertNotNull("GC discarded the move associations", Associations.Data(clazz))
    originalTree
  }

  // Keep getNode's temporary strong reference out of the frame that forces GC.
  private def weakTree(clazz: PsiClass): WeakReference[FileASTNode] =
    new WeakReference(clazz.getContainingFile.getNode)

  private def forceGc(): Unit = {
    val probe = new SoftReference(new Object)
    GCUtil.tryGcSoftlyReachableObjects()
    assertTrue("The GC probe must collect softly reachable objects", probe.refersTo(null))
  }
}
