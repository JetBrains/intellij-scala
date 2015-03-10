package org.jetbrains.plugins.scala
package lang
package psi
package impl


import java.util

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.roots._
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.ResolveScopeManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.util.{PsiModificationTracker, PsiUtilCore}
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.decompiler.{CompiledFileAdjuster, DecompilerUtil}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScToplevelElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScControlFlowOwner, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFileStub
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class ScalaFileImpl(viewProvider: FileViewProvider, fileType: LanguageFileType = ScalaFileType.SCALA_FILE_TYPE)
        extends PsiFileBase(viewProvider, fileType.getLanguage)
                with ScalaFile with FileDeclarationsHolder 
                with CompiledFileAdjuster with ScControlFlowOwner with FileResolveScopeProvider {
  override def getViewProvider = viewProvider

  override def getFileType = fileType

  override def toString = "ScalaFile:" + getName

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  def isCompiled = compiled

  def sourceName: String = {
    if (isCompiled) {
      val stub = getStub
      if (stub != null) {
        return stub.getFileName
      }
      val virtualFile = getVirtualFile
      DecompilerUtil.decompile(virtualFile, virtualFile.contentsToByteArray).sourceName
    }
    else ""
  }

  override def getName: String = {
    if (virtualFile != null) virtualFile.getName
    else super.getName
  }

  override def getVirtualFile: VirtualFile = {
    if (virtualFile != null) virtualFile
    else super.getVirtualFile
  }

  override def getNavigationElement: PsiElement = {
    if (!isCompiled) this
    else {
      val inner: String = getPackageNameInner
      val pName = inner + typeDefinitions.find(_.isPackageObject).map((if (inner.length > 0) "." else "") + _.name).getOrElse("")
      val sourceFile = sourceName
      val relPath = if (pName.length == 0) sourceFile else pName.replace(".", "/") + "/" + sourceFile

      // Look in libraries' sources
      val vFile = getContainingFile.getVirtualFile
      val index = ProjectRootManager.getInstance(getProject).getFileIndex
      val entries = index.getOrderEntriesForFile(vFile).toArray(OrderEntry.EMPTY_ARRAY)
      var entryIterator = entries.iterator
      while (entryIterator.hasNext) {
        val entry = entryIterator.next()
        // Look in sources of an appropriate entry
        val files = entry.getFiles(OrderRootType.SOURCES)
        val filesIterator = files.iterator
        while (!filesIterator.isEmpty) {
          val file = filesIterator.next()
          val source = file.findFileByRelativePath(relPath)
          if (source != null) {
            val psiSource = getManager.findFile(source)
            psiSource match {
              case o: PsiClassOwner => return o
              case _ =>
            }
          }
        }
      }
      entryIterator = entries.iterator

      //Look in libraries sources if file not relative to path
      if (typeDefinitions.length == 0) return this
      val qual = typeDefinitions.apply(0).qualifiedName
      var result: Option[PsiFile] = None

      FilenameIndex.processFilesByName(sourceFile, false, new Processor[PsiFileSystemItem] {
        override def process(t: PsiFileSystemItem): Boolean = {
          val source = t.getVirtualFile
          getManager.findFile(source) match {
            case o: ScalaFile =>
              val clazzIterator = o.typeDefinitions.iterator
              while (clazzIterator.hasNext) {
                val clazz = clazzIterator.next()
                if (qual == clazz.qualifiedName) {
                  result = Some(o)
                  return false
                }
              }
            case o: PsiClassOwner =>
              val clazzIterator = o.getClasses.iterator
              while (clazzIterator.hasNext) {
                val clazz = clazzIterator.next()
                if (qual == clazz.qualifiedName) {
                  result = Some(o)
                  return false
                }
              }
            case _ =>
          }
          true
        }
      }, GlobalSearchScope.allScope(getProject), getProject, null)

      result match {
        case Some(o) => o
        case _ => this
      }
    }
  }

  private def isScriptFileImpl: Boolean = {
    val stub = getStub
    if (stub == null) {
      val empty = children.forall {
        case _: PsiWhiteSpace => true
        case _: PsiComment => true
        case _ => false
      }
      if (empty) return true // treat empty or commented files as scripts to avoid project recompilations
      val childrenIterator = getNode.getChildren(null).iterator
      while (childrenIterator.hasNext) {
        val n = childrenIterator.next()
        n.getPsi match {
          case _: ScPackaging => return false
          case _: ScValue | _: ScVariable | _: ScFunction | _: ScExpression | _: ScTypeAlias => return true
          case _ => if (n.getElementType == ScalaTokenTypes.tSH_COMMENT) return true
        }
      }
      false
    } else {
      stub.isScript
    }
  }

  def isScriptFile: Boolean = isScriptFile(withCashing = true)

  def isScriptFile(withCashing: Boolean): Boolean = {
    if (!withCashing) return isScriptFileImpl
    CachesUtil.get(this, CachesUtil.IS_SCRIPT_FILE_KEY,
      new CachesUtil.MyProvider(this, (file: ScalaFileImpl) => file.isScriptFileImpl)(this))
  }

  def isWorksheetFile: Boolean = {
    this.getVirtualFile != null && this.getVirtualFile.getExtension == ScalaFileType.WORKSHEET_EXTENSION
  }


  def setPackageName(name: String) {
    // TODO support multiple base packages simultaneously
    val basePackageName = {
      val basePackages = ScalaProjectSettings.getInstance(getProject).getBasePackages.asScala
      basePackages.find(name.startsWith).getOrElse("")
    }

    this match {
      // Handle package object
      case TypeDefinitions(obj: ScObject) if obj.isPackageObject && obj.name != "`package`" =>
        val (packageName, objectName) = name match {
          case ScalaFileImpl.QualifiedPackagePattern(qualifier, simpleName) => (qualifier, simpleName)
          case s => ("", name)
        }

        setPackageName(basePackageName, packageName)
        typeDefinitions.headOption.foreach(_.name = objectName)

      case _ => setPackageName(basePackageName, name)
    }
  }

  def setPackageName(base: String, name: String) {
    if (packageName == null) return

    val vector = ScalaFileImpl.toVector(name)

    preservingClasses {
      val documentManager = PsiDocumentManager.getInstance(getProject)
      val document = documentManager.getDocument(this)

      val prefixText = children.findByType(classOf[ScPackaging])
              .map(it => getText.substring(0, it.getTextRange.getStartOffset))
              .filter(!_.isEmpty)

      try {
        stripPackagings(document)
        if (vector.nonEmpty) {
          val packagingsText = {
            val path = {
              val splits = ScalaFileImpl.toVector(base) :: ScalaFileImpl.splitsIn(ScalaFileImpl.pathIn(this))
              splits.foldLeft(List(vector))(ScalaFileImpl.splitAt)
            }
            path.map(_.mkString("package ", ".", "")).mkString("", "\n", "\n\n")
          }

          prefixText.foreach(s => document.deleteString(0, s.length))
          document.insertString(0, packagingsText)
          prefixText.foreach(s => document.insertString(0, s))
        }
      } finally {
        documentManager.commitDocument(document)
      }
    }
  }

  private def preservingClasses(block: => Unit) {
    val data = typeDefinitions

    block

    for ((aClass, oldClass) <- typeDefinitions.zip(data)) {
      CodeEditUtil.setNodeGenerated(oldClass.getNode, true)
      PostprocessReformattingAspect.getInstance(getProject).disablePostprocessFormattingInside {
        new Runnable {
          def run() {
            aClass.getNode.getTreeParent.replaceChild(aClass.getNode, oldClass.getNode)
          }
        }
      }
    }
  }

  private def stripPackagings(document: Document) {
    depthFirst.findByType(classOf[ScPackaging]).foreach { p =>
      val startOffset = p.getTextOffset
      val endOffset = startOffset + p.getTextLength
      document.replaceString(startOffset, endOffset, p.getBodyText.trim)
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
      stripPackagings(document)
    }
  }

  override def getStub: ScFileStub = super[PsiFileBase].getStub match {
    case null => null
    case s: ScFileStub => s
    case _ =>
      val faultyContainer: VirtualFile = PsiUtilCore.getVirtualFile(this)
      ScalaFileImpl.LOG.error("Scala File has wrong stub file: " + faultyContainer)
      if (faultyContainer != null && faultyContainer.isValid) {
        FileBasedIndex.getInstance.requestReindex(faultyContainer)
      }
      null
  }

  def getPackagings: Array[ScPackaging] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.PACKAGING, JavaArrayFactoryUtil.ScPackagingFactory)
    } else findChildrenByClass(classOf[ScPackaging])
  }

  def getPackageName: String = {
    val res = packageName
    if (res == null) ""
    else res
  }

  @Nullable
  def packageName: String = {
    if (isScriptFile || isWorksheetFile) return null
    var res: String = ""
    var x: ScToplevelElement = this
    while (true) {
      val packs: Seq[ScPackaging] = x.packagings
      if (packs.length > 1) return null
      else if (packs.length == 0) return if (res.length == 0) res else res.substring(1)
      res += "." + packs(0).getPackageName
      x = packs(0)
    }
    null //impossible line
  }

  private def getPackageNameInner: String = {
    val ps = getPackagings

    def inner(p: ScPackaging, prefix: String): String = {
      val subs = p.packagings
      if (subs.length > 0 && !subs(0).isExplicit) inner(subs(0), prefix + "." + subs(0).getPackageName)
      else prefix
    }

    if (ps.length > 0 && !ps(0).isExplicit) {
      val prefix = ps(0).getPackageName
      inner(ps(0), prefix)
    } else ""
  }

  override def getClasses: Array[PsiClass] = {
    if (!isScriptFile && !isWorksheetFile) {
      if (ScalaFileImpl.isDuringMoveRefactoring) {
        return typeDefinitions.toArray
      }
      val arrayBuffer = new ArrayBuffer[PsiClass]()
      for (definition <- typeDefinitions) {
        arrayBuffer += definition
        definition match {
          case o: ScObject =>
            o.fakeCompanionClass match {
              case Some(clazz) => arrayBuffer += clazz
              case _ =>
            }
          case t: ScTrait => arrayBuffer += t.fakeCompanionClass
          case c: ScClass =>
            c.fakeCompanionModule match {
              case Some(m) => arrayBuffer += m
              case _ =>
            }
          case _ =>
        }
      }
      arrayBuffer.toArray
    } else PsiClass.EMPTY_ARRAY
  }

  def icon = Icons.FILE_TYPE_LOGO

  protected def isScalaPredefinedClass = {
    def inner(file: ScalaFile): java.lang.Boolean = {
      java.lang.Boolean.valueOf(file.typeDefinitions.length == 1 &&
        Set("scala", "scala.Predef").contains(file.typeDefinitions.apply(0).qualifiedName))
    }
    CachesUtil.get[ScalaFile, java.lang.Boolean](this, CachesUtil.SCALA_PREDEFINED_KEY,
      new CachesUtil.MyProvider[ScalaFile, java.lang.Boolean](this, e => inner(e))
      (PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT)).booleanValue()
  } 
  
  
  def isScalaPredefinedClassInner = typeDefinitions.length == 1 &&
    Set("scala", "scala.Predef").contains(typeDefinitions.apply(0).qualifiedName)


  override def findReferenceAt(offset: Int): PsiReference = super.findReferenceAt(offset)

  override def controlFlowScope(): Option[ScalaPsiElement] = Some(this)

  def getClassNames: util.Set[String] = {
    val res = new util.HashSet[String]
    typeDefinitions.foreach {
      case clazz: ScClass => res.add(clazz.getName)
      case o: ScObject =>
        res.add(o.getName)
        o.fakeCompanionClass match {
          case Some(clazz) => res.add(clazz.getName)
          case _ =>
        }
      case t: ScTrait =>
      res.add(t.getName)
      res.add(t.fakeCompanionClass.getName)
    }
    res
  }

  def packagingRanges: Seq[TextRange] =
    depthFirst.filterByType(classOf[ScPackaging]).flatMap(_.reference).map(_.getTextRange).toList

  def getFileResolveScope: GlobalSearchScope = {
    val vFile = getOriginalFile.getVirtualFile
    if (vFile == null) GlobalSearchScope.allScope(getProject)
    else {
      val resolveScopeManager = ResolveScopeManager.getInstance(getProject)
      resolveScopeManager.getDefaultResolveScope(vFile)
    }
  }

  def ignoreReferencedElementAccessibility(): Boolean = true //todo: ?

  override def setContext(element: PsiElement, child: PsiElement) {
    putCopyableUserData(ScalaFileImpl.CONTEXT_KEY, element)
    putCopyableUserData(ScalaFileImpl.CHILD_KEY, child)
  }

  override def getContext: PsiElement = {
    getCopyableUserData(ScalaFileImpl.CONTEXT_KEY) match {
      case null => super.getContext
      case _ => getCopyableUserData(ScalaFileImpl.CONTEXT_KEY)
    }
  }

  override def getPrevSibling: PsiElement = {
    getCopyableUserData(ScalaFileImpl.CHILD_KEY) match {
      case null => super.getPrevSibling
      case _ => getCopyableUserData(ScalaFileImpl.CHILD_KEY).getPrevSibling
    }
  }

  override def getNextSibling: PsiElement = {
    child match {
      case null => super.getNextSibling
      case _ => getCopyableUserData(ScalaFileImpl.CHILD_KEY).getNextSibling
    }
  }

  override protected def insertFirstImport(importSt: ScImportStmt, first: PsiElement): PsiElement = {
    if (isScriptFile) {
      first match {
        case c: PsiComment if c.getNode.getElementType == ScalaTokenTypes.tSH_COMMENT => addImportAfter(importSt, c)
        case _ => super.insertFirstImport(importSt, first)
      }
    } else {
      super.insertFirstImport(importSt, first)
    }
  }
}

object ScalaFileImpl {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl")
  private val QualifiedPackagePattern = "(.+)\\.(.+?)".r
  val SCRIPT_KEY = new Key[java.lang.Boolean]("Is Script Key")
  val CONTEXT_KEY = new Key[PsiElement]("context.key")
  val CHILD_KEY = new Key[PsiElement]("child.key")

  val DefaultImplicitlyImportedPackges = Seq("scala", "java.lang")

  val DefaultImplicitlyImportedObjects = Seq("scala.Predef", "scala" /* package object*/)

  /**
   * @param _place actual place, can be null, if null => false
   * @return true, if place is out of source content root, or in Scala Worksheet.
   */
  def isProcessLocalClasses(_place: PsiElement): Boolean = {
    val place = _place match {
      case s: ScalaPsiElement => s.getDeepSameElementInContext
      case _ => _place
    }
    if (place == null) return false
    val containingFile: PsiFile = place.getContainingFile
    if (containingFile == null) return false
    containingFile match {
      case s: ScalaFile =>
        if (s.isWorksheetFile) return true
        val file: VirtualFile = s.getVirtualFile
        if (file == null) return false
        val index = ProjectRootManager.getInstance(place.getProject).getFileIndex
        !(index.isInSourceContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file))
      case _ => false
    }
  }

  def pathIn(root: PsiElement): List[List[String]] =
    packagingsIn(root).map(packaging => toVector(packaging.getPackageName))

  private def packagingsIn(root: PsiElement): List[ScPackaging] = {
    root.children.findByType(classOf[ScPackaging]).headOption match {
      case Some(packaging) => packaging :: packagingsIn(packaging)
      case _ => Nil
    }
  }

  def splitsIn(path: List[List[String]]): List[List[String]] =
    path.scanLeft(List[String]())((vs, v) => vs ::: v).tail.dropRight(1)

  def splitAt(path: List[List[String]], vector: List[String]): List[List[String]] = {
    if (vector.isEmpty) path else path match {
      case h :: t if h == vector => h :: t
      case h :: t if vector.startsWith(h) => h :: splitAt(t, vector.drop(h.size))
      case h :: t if h.startsWith(vector) => h.take(vector.size) :: h.drop(vector.size) :: t
      case it => it
    }
  }

  def toVector(name: String): List[String] = if (name.isEmpty) Nil else name.split('.').toList

  private[this] var duringMoveRefactoring: Boolean = false

  private def isDuringMoveRefactoring: Boolean = duringMoveRefactoring

  def performMoveRefactoring(body: => Unit) {
    synchronized {
      try {
        duringMoveRefactoring = true
        body
      } finally {
        duringMoveRefactoring = false
      }
    }
  }
}
