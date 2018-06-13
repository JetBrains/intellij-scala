package org.jetbrains.plugins.scala.refactoring.move.member

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.{PsiDocumentManager, PsiMember}
import com.intellij.refactoring.move.moveMembers.{MoveMembersOptions, MoveMembersProcessor}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions.TraversableExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
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

    val members = source.members.filterBy[ScDeclaredElementsHolder]
    val aMember = members.find(m => m.declaredNames.contains(memberName)).get

    ScalaFileImpl.performMoveRefactoring {
      new MoveMembersProcessor(getProjectAdapter, new MoveMembersOptions() {
        override def getSelectedMembers: Array[PsiMember] = Seq(aMember.asInstanceOf[PsiMember]).toArray

        override def getMemberVisibility: String = "public"

        override def makeEnumConstant(): Boolean = false

        override def getTargetClassName: String = target.getQualifiedName
      }).run()
    }
    FileDocumentManager.getInstance.saveAllDocuments()
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
  }
}
