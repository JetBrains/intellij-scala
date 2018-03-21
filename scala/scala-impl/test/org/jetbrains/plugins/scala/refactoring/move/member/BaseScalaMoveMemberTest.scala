package org.jetbrains.plugins.scala.refactoring.move.member

import java.io.File
import java.util

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.{LocalFileSystem, VfsUtil, VirtualFile}
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{JavaPsiFacade, PsiDocumentManager, PsiMember}
import com.intellij.refactoring.move.moveMembers.{MoveMembersOptions, MoveMembersProcessor}
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.util.TestUtils

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

abstract class BaseScalaMoveMemberTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  protected def folderPath = baseRootPath() + "moveMember/"

  private def root = TestUtils.getTestDataPath + "/moveMember/" + getTestName(true) + "/"

  private def rootBefore = root + "before/"

  private def rootAfter = root + "after/"

  private var rootDirBefore: VirtualFile = _
  private var rootDirAfter: VirtualFile = _



  def doTest(fromObject: String, toObject: String, memberName: String) = {
    LocalFileSystem.getInstance().refresh(false)
    try {
      performAction(fromObject, toObject, memberName)
    } finally {
      PsiTestUtil.removeSourceRoot(getModuleAdapter, rootDirBefore)
    }
    getProjectAdapter.getComponent(classOf[PostprocessReformattingAspect]).doPostponedFormatting()
    PlatformTestUtil.assertDirectoriesEqual(rootDirAfter, rootDirBefore)
  }

  private def performAction(fromObject: String, toObject: String, memberName: String): Unit = {
    val aClass = JavaPsiFacade.getInstance(getProjectAdapter).findClass(fromObject, GlobalSearchScope.moduleScope(getModuleAdapter))
    assert(aClass != null, s"file $fromObject not found")
    val members = PsiTreeUtil.findChildrenOfAnyType(aClass, classOf[ScFunctionDefinition], classOf[ScVariableDefinition], classOf[ScPatternDefinition])
    val aMember = members.asScala.find(m => m.declaredNames.contains(memberName)).get
    ScalaFileImpl.performMoveRefactoring {
      new MoveMembersProcessor(getProjectAdapter, new MoveMembersOptions() {
        override def getSelectedMembers: Array[PsiMember] = Seq(aMember.asInstanceOf[PsiMember]).toArray

        override def getMemberVisibility: String = "public"

        override def makeEnumConstant(): Boolean = false

        override def getTargetClassName: String = toObject
      }).run()
    }
    FileDocumentManager.getInstance.saveAllDocuments()
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
