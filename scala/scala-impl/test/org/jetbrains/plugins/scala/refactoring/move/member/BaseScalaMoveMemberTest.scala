package org.jetbrains.plugins.scala.refactoring.move.member

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.refactoring.move.members.ScalaMoveMembersDialog
import org.junit.Assert


abstract class BaseScalaMoveMemberTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def doTest(fromObject: String, toObject: String, memberName: String, fileText: String, expectedText: String): Unit = {
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))
    performAction(fromObject, toObject, memberName)
    Assert.assertEquals(normalize(expectedText), getFileAdapter.getText)
  }

  private def performAction(fromObject: String, toObject: String, memberName: String): Unit = {
    val projectScope = ElementScope(getProjectAdapter)
    val source = projectScope.getCachedObject(fromObject).orNull
    val target = projectScope.getCachedObject(toObject).orNull

    Assert.assertTrue(s"file $fromObject not found", source != null)
    Assert.assertTrue(s"file $target not found", target != null)

    val member = source.membersWithSynthetic.find(_.names.contains(memberName)).get

    val processor = ScalaMoveMembersDialog.createProcessor(target, member)
    ScalaFileImpl.performMoveRefactoring(processor.run())

    FileDocumentManager.getInstance.saveAllDocuments()
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
  }
}
