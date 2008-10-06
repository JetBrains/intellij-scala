package org.jetbrains.plugins.scala.lang.psi.impl.compiled

import api.ScalaFile
import api.toplevel.packaging.{ScPackaging, ScPackageStatement}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.{StdFileTypes, FileType}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.compiled.ClsRepositoryPsiElement
import com.intellij.psi.impl.compiled.ClsElementImpl
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.stubs.{StubElement, PsiClassHolderFileStub, StubTree, PsiFileStubImpl}
import com.intellij.util.IncorrectOperationException
import java.lang.ref.WeakReference
import parser.{ErrMsg, ScalaElementTypes}
import stubs.impl.ScFileStubImpl
import stubs.ScFileStub
import com.intellij.psi._

/**
 * @author ilyas
 */

class ScClsFileImpl(stub: ScFileStub) extends ClsRepositoryPsiElement[ScFileStub](stub) with ScalaFile {

  private var myManager: PsiManagerImpl = null
  private var myIsForDecompiling: Boolean = false
  private var myViewProvider: FileViewProvider = null
  object lock
  @volatile
  private var myPackageStatement: ScClsPackageStatementImpl = null;
  @volatile
  private var myStub: WeakReference[StubTree] = null

  def this(manager: PsiManagerImpl, provider: FileViewProvider, forDecompiling: Boolean) = {
    this (null);
    myManager = manager;
    ScalaElementTypes.ANNOTATION

    myIsForDecompiling = forDecompiling
    myViewProvider = provider
  }

  def this(manager: PsiManagerImpl, prov: FileViewProvider) = this (manager, prov, false)

  override def getManager: PsiManager = myManager

  def getPackagings: Array[ScPackaging] = Array[ScPackaging]()

  def getVirtualFile: VirtualFile = myViewProvider.getVirtualFile

  override def getText = {
    initMirror
    myMirror.getText
  }

  override def textToCharArray: Array[Char] = {
    initMirror
    myMirror.textToCharArray
  }

  def initMirror {
    if (myMirror == null) {
      val documentManager = FileDocumentManager.getInstance
      val document = documentManager.getDocument(getVirtualFile)
      val text = document.getText();
      val ext = ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension
      val classes = getClasses
      val aClass = classes(0)
      val fileName = aClass.getName() + "." + ext
      val manager = getManager()
      val mirror = PsiFileFactory.getInstance(manager.getProject).createFileFromText(fileName, text)
      val mirrorTreeElement = SourceTreeToPsiMap.psiElementToTree(mirror)

      //IMPORTANT: do not take lock too early - FileDocumentManager.getInstance().saveToString() can run write action...
      PsiLock.LOCK.synchronized{
        if (myMirror == null) {
          setMirror(mirrorTreeElement.asInstanceOf[TreeElement])
        }
      }
    }

  }

  def processChildren(processor: PsiElementProcessor[PsiFileSystemItem]): Boolean = true

  override def getParent: PsiDirectory = getContainingDirectory

  def getContainingDirectory: PsiDirectory = {
    val parent = getVirtualFile.getParent
    if (parent == null) return null
    return getManager.findDirectory(parent)
  }

  override def isValid: Boolean = {
    if (myIsForDecompiling) return true
    getVirtualFile.isValid
  }

  def getName: String = getVirtualFile.getName

  def accept(visitor: PsiElementVisitor): Unit = {
    visitor.visitFile(this)
  }

  def getOriginalFile: PsiFile = null

  def getFileType: FileType = StdFileTypes.CLASS

  def getViewProvider: FileViewProvider = myViewProvider

  def setMirror(element: TreeElement): Unit = {
    myMirror = element
    val mirrorFile = SourceTreeToPsiMap.treeElementToPsi(myMirror)
    mirrorFile match {
      case sf: ScalaFile => {
        // treat package statement
        val packStatementMirror = sf.packageStatement
        val pst = packageStatement
        (pst, packStatementMirror) match {
          case (Some(p_this), Some(p_other)) => {
            p_this.asInstanceOf[ClsElementImpl].setMirror(SourceTreeToPsiMap.psiElementToTree(p_other).asInstanceOf[TreeElement])
          }
          case _ =>
        }

        //treat classes
        val classes = getClasses
        if (classes.length == 1) {
          if (JavaPsiFacade.getInstance(getProject).getNameHelper.isIdentifier(classes(0).getName)) {
            return
          }
        }

        val mirrorClasses = sf.getClasses
        if (classes.length > 0 && classes.length == mirrorClasses.length) {
          for (i <- 0 to classes.length - 1) {
            classes(i).asInstanceOf[ClsElementImpl].setMirror(SourceTreeToPsiMap.psiElementToTree(mirrorClasses(i)).asInstanceOf[TreeElement])
          }
        }

      }
      case _ =>
    }
  }

  def getPsiRoots: Array[PsiFile] = Array[PsiFile](this)

  def getModificationStamp: Long = getVirtualFile.getModificationStamp

  def packageStatement = {
    if (myPackageStatement == null) {
      myPackageStatement = new ScClsPackageStatementImpl(this)
    }
    if (myPackageStatement.getPackageName != null) Some(myPackageStatement) else None
  }

  override def getStub: ScFileStub = {
    val stubHolder = getStubTree
    if (stubHolder != null) stubHolder.getRoot.asInstanceOf[ScFileStub] else null
  }

  def subtreeChanged: Unit = {}

  def getStubTree: StubTree = {
    var stub: WeakReference[StubTree] = myStub;
    var stubHolder: StubTree = if (stub == null) null else stub.get
    if (stubHolder == null) {
      lock.synchronized{
        stub = myStub
        stubHolder = if (stub == null) null else stub.get
        if (stubHolder != null) return stubHolder
        stubHolder = StubTree.readFromVFile(getVirtualFile())
        if (stubHolder != null) {
          myStub = new WeakReference[StubTree](stubHolder);
          (stubHolder.getRoot.asInstanceOf[ScFileStubImpl]).setPsi(this);
        }
      }
    }
    stubHolder
  }

  def getPackageName: String = packageStatement match {
    case Some(p) => p.getPackageName
    case None => ""
  }

  def getClasses: Array[PsiClass] = {
    val stub = getStub
    if (stub != null) stub.getClasses else PsiClass.EMPTY_ARRAY
  }

  def appendMirrorText(indentLevel: Int, buffer: StringBuffer): Unit = {
    buffer.append(ErrMsg("psi.decompiled.text.header"))
    goNextLine(indentLevel, buffer)
    goNextLine(indentLevel, buffer)
    packageStatement match {
      case Some(pst) => {
        pst.asInstanceOf[ClsElementImpl].appendMirrorText(0, buffer)
        goNextLine(indentLevel, buffer)
        goNextLine(indentLevel, buffer)
      }
      case None =>
    }

    val classes = getClasses

    for (clazz <- classes) {
      clazz.asInstanceOf[ClsElementImpl].appendMirrorText(0, buffer)
      goNextLine(indentLevel, buffer)
      goNextLine(indentLevel, buffer)
    }

  }

  def goNextLine(indentLevel: Int, buffer: StringBuffer) {
    buffer.append('\n')
    for (i <- 1 to indentLevel) buffer.append(' ')
  }


  override def getChildren = getClasses.asInstanceOf[Array[PsiElement]]

  override def getContainingFile: PsiFile = if (!isValid) throw new PsiInvalidElementAccessException(this) else this

  def findTreeForStub(tree: StubTree, stub: StubElement[_]) = null

  def setPackageName(packageName: String): Unit = throw new IncorrectOperationException("Cannot set package name for compiled files")

  def checkSetName(name: String): Unit = throw new IncorrectOperationException("Cannot modify compiled element")

  def setName(name: String): PsiElement = throw new IncorrectOperationException("Cannot modify compiled element")

  def isDirectory: Boolean = false

  protected def findChildByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = null

  protected def findChildrenByClass[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = Array[T]()
}