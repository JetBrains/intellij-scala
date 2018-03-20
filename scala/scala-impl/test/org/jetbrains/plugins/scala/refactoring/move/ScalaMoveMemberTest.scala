package org.jetbrains.plugins.scala.refactoring.move

import java.io.File
import java.util

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiMember}
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.move.moveMembers.{MoveMembersOptions, MoveMembersProcessor}
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.refactoring.move.members.ScalaMoveMemberHandler
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TestUtils

class ScalaMoveMemberTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val caretMarker = "/*caret*/"

  protected def folderPath = baseRootPath() + "moveMember/"

  private def root = TestUtils.getTestDataPath + "/moveMember/" + getTestName(true) + "/"

  private def rootBefore = root + "before/"

  private def rootAfter = root + "after/"

  private var rootDirBefore: VirtualFile = _
  private var rootDirAfter: VirtualFile = _

  def testSimple() = {
    doTest
  }

  def doTest() = {
    val moveCompanion = true
    val settings = ScalaApplicationSettings.getInstance()
    val moveCompanionOld = settings.MOVE_COMPANION
    settings.MOVE_COMPANION = moveCompanion
    try {
      performAction()
    } finally {
      PsiTestUtil.removeSourceRoot(getModuleAdapter, rootDirBefore)
    }
    settings.MOVE_COMPANION = moveCompanionOld
    getProjectAdapter.getComponent(classOf[PostprocessReformattingAspect]).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(rootDirAfter, rootDirBefore)
  }

  private def performAction() = {
    val filePath = rootBefore + "A.scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val offset = fileText.indexOf(caretMarker) + caretMarker.length
    assert(offset != -1, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    val editorA = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContextFromFocus.getResult)
    editorA.getCaretModel.moveToOffset(offset + 1)

    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val element = CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContextFromFocus.getResult)
    val targetObject = ElementScope(getProjectAdapter).getCachedObject("C").get

    //    new MoveMembersProcessor(getProjectAdapter, new MoveMembersOptions() {
    //      override def getSelectedMembers: Array[PsiMember] = Seq(element.asInstanceOf[PsiMember]).toArray
    //
    //      override def getMemberVisibility: String = ""
    //
    //      override def makeEnumConstant(): Boolean = ???
    //
    //      override def getTargetClassName: String = "B"
    //    }).run()

    ScalaFileImpl.performMoveRefactoring {
      new ScalaMoveMemberHandler().doMove(new MoveMembersOptions() {
        override def getSelectedMembers: Array[PsiMember] = Seq(element.asInstanceOf[PsiMember]).toArray

        override def getMemberVisibility: String = null

        override def makeEnumConstant(): Boolean = ???

        override def getTargetClassName: String = "C"
      }, element.asInstanceOf[PsiMember], null, targetObject)
    }
    PsiDocumentManager.getInstance(getProjectAdapter).commitAllDocuments()
  }

  override protected def afterSetUpProject() = {
    super.afterSetUpProject()
    findAndRefreshVFile(rootBefore)
    rootDirBefore = PsiTestUtil.createTestProjectStructure(getProjectAdapter, getModuleAdapter, rootBefore, new util.HashSet[File]())
    rootDirAfter = findAndRefreshVFile(rootAfter)
  }

  private def findAndRefreshVFile(path: String) = {
    val vFile = LocalFileSystem.getInstance.findFileByPath(path.replace(File.separatorChar, '/'))
    VfsUtil.markDirtyAndRefresh(/*async = */ false, /*recursive =*/ true, /*reloadChildren =*/ true, vFile)
    vFile
  }
}
