package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.{PostprocessReformattingAspect, codeStyle}
import com.intellij.psi.impl.{DebugUtil, ResolveScopeManager}
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.{FileResolveScopeProvider, FileViewProvider, PsiClass, PsiDocumentManager, PsiElement, PsiReference}
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.{ResolveFilterScope, WorksheetResolveFilterScope}
import org.jetbrains.plugins.scala.lang.TokenSets._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFileStub
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.{JavaArrayFactoryUtil, ScalaFileType}

import java.{util => ju}
import scala.annotation.{nowarn, tailrec}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

class ScalaFileImpl(
  viewProvider: FileViewProvider,
  override val getFileType: LanguageFileType,
  language: Language
) extends PsiFileBase(viewProvider, language)
    with ScalaFile
    with FileDeclarationsHolder
    with ScDeclarationSequenceHolder
    with ScControlFlowOwner
    with FileResolveScopeProvider {

  def this(viewProvider: FileViewProvider, fileType: LanguageFileType = ScalaFileType.INSTANCE) =
    this(viewProvider, fileType, fileType.getLanguage)

  import ScalaFileImpl._
  import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitFile(this)
  }

  override def isCompiled: Boolean = false

  override def toString: String = "ScalaFile: " + getName

  override protected final def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected final def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    findChildByClass[T](clazz)

  override final def getName: String = super.getName

  override def isMultipleDeclarationsAllowed: Boolean = false

  override def isWorksheetFile: Boolean = false

  override def setPackageName(inName: String): Unit = {
    val basePackageName =
      this.module.map(ScalaProjectSettings.getInstance(getProject).getBasePackageFor).getOrElse("")

    val name = ScalaNamesUtil.escapeKeywordsFqn(inName)

    typeDefinitions match {
      // Handle package object
      case Seq(obj: ScObject) if obj.isPackageObject && obj.name != "`package`" =>
        val (packageName, objectName) = name match {
          case QualifiedPackagePattern(qualifier, simpleName) => (qualifier, simpleName)
          case _ => ("", name)
        }

        setPackageName(basePackageName, packageName)
        obj.setName(objectName)
      case _ => setPackageName(basePackageName, name)
    }
  }

  def setPackageName(base: String, name: String): Unit = {
    if (packageName == null) return

    val vector = toVector(name)

    preservingClasses {
      val documentManager = PsiDocumentManager.getInstance(getProject)
      val document = documentManager.getDocument(this)

      val prefixText = this.children.findByType[ScPackaging]
              .map(it => getText.substring(0, it.getTextRange.getStartOffset))
              .filter(!_.isEmpty)

      try {
        stripPackagings(document)
        if (vector.nonEmpty) {
          val packagingsText = {
            val path = {
              val splits = toVector(base) :: splitsIn(pathIn(this))
              splits.foldLeft(List(vector))(splitAt)
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

  private def preservingClasses(block: => Unit): Unit = {
    val data = this.typeDefinitions

    block

    for ((aClass, oldClass) <- this.typeDefinitions.zip(data)) {
      codeStyle.CodeEditUtil.setNodeGenerated(oldClass.getNode, true)
      PostprocessReformattingAspect.getInstance(getProject).disablePostprocessFormattingInside {
        new Runnable {
          override def run(): Unit = {
            try {
              DebugUtil.startPsiModification(null): @nowarn("cat=deprecation")
              aClass.getNode.getTreeParent.replaceChild(aClass.getNode, oldClass.getNode)
            }
            finally {
              DebugUtil.finishPsiModification(): @nowarn("cat=deprecation")
            }
          }
        }
      }
    }
  }

  private def stripPackagings(document: Document): Unit = {
    this.depthFirst().findByType[ScPackaging].foreach { p =>
      val startOffset = p.getTextOffset
      val endOffset = startOffset + p.getTextLength
      document.replaceString(startOffset, endOffset, p.bodyText.trim)
      PsiDocumentManager.getInstance(getProject).commitDocument(document)
      stripPackagings(document)
    }
  }

  override def getStub: ScFileStub = super[PsiFileBase].getStub match {
    case null => null
    case s: ScFileStub => s
    case _ =>
      val faultyContainer: VirtualFile = PsiUtilCore.getVirtualFile(this)
      LOG.error("Scala File has wrong stub file: " + faultyContainer)
      if (faultyContainer != null && faultyContainer.isValid) {
        FileBasedIndex.getInstance.requestReindex(faultyContainer)
      }
      null
  }

  override def firstPackaging: Option[ScPackaging] = packagings.headOption

  protected def packagings: Seq[ScPackaging] = foldStub(findChildren[ScPackaging]) { stub =>
    ArraySeq.unsafeWrapArray(stub.getChildrenByType(PACKAGING, JavaArrayFactoryUtil.ScPackagingFactory))
  }

  override def getPackageName: String = packageName match {
    case null => ""
    case name => name
  }

  private def packageName: String = {
    if (isWorksheetFile) return null

    @tailrec
    def inner(packagings: Seq[ScPackaging], result: StringBuilder): String =
      packagings match {
        case Seq() => if (result.isEmpty) "" else result.substring(1)
        case Seq(head) =>
          inner(head.packagings, result.append(".").append(head.packageName))
        case _ => null
      }

    inner(packagings, new StringBuilder())
  }

  //Among other use places, this method is used to determine icon for a file in Project View
  override def getClasses: Array[PsiClass] =
    if (isWorksheetFile)
      PsiClass.EMPTY_ARRAY
    else {
      val myMembers = members
      val myTypeDefinitions = myMembers.filterByType[ScTypeDefinition]
      /*if (myMembers.size != myTypeDefinitions.size)
        PsiClass.EMPTY_ARRAY
      else */if (isDuringMoveRefactoring)
        myTypeDefinitions.toArray
      else {
        val result = myTypeDefinitions.flatMap { definition =>
          val companions = definition match {
            case o: ScObject => o.fakeCompanionClass.toList
            case t: ScTrait  => t.fakeCompanionClass :: t.fakeCompanionModule.toList
            case c: ScClass  => c.fakeCompanionModule.toList
            case _ => Nil
          }
          definition :: companions
        }
        result.toArray
      }
    }

  override def findReferenceAt(offset: Int): PsiReference = super.findReferenceAt(offset)

  override def controlFlowScope: Option[ScalaPsiElement] = Some(this)

  override def getClassNames: ju.Set[String] = {
    typeDefinitions.toSet[ScTypeDefinition].flatMap { definition =>
      val classes = definition :: (definition match {
        case _: ScClass => Nil
        case scalaObject: ScObject => scalaObject.fakeCompanionClass.toList
        case scalaTrait: ScTrait => scalaTrait.fakeCompanionClass :: Nil
      })
      classes.map(_.getName)
    }.asJava
  }

  override def packagingRanges: Seq[TextRange] =
    this.depthFirst().filterByType[ScPackaging].flatMap(_.reference).map(_.getTextRange).toList

  /**
   * ATM used in a single place: [[com.intellij.psi.impl.source.resolve.JavaResolveUtil.ignoreReferencedElementAccessibility]]
   * Usage example: [[com.siyeh.ig.classlayout.ProtectedMemberInFinalClassInspection.WeakenVisibilityFix]] (SCL-19756)
   */
  override def ignoreReferencedElementAccessibility(): Boolean = false

  /**
   * Reminder some logic is also located in implementations of [[com.intellij.psi.ResolveScopeEnlarger]]
   *
   * Note: consider replacing `FileResolveScopeProvider.getFileResolveScope` with [[com.intellij.psi.ResolveScopeEnlarger]]
   */
  override def getFileResolveScope: GlobalSearchScope = {
    implicit val project: Project = getProject
    val file = getOriginalFile.getVirtualFile
    if (file != null && file.isValid) {
      val defaultResolveScope = defaultFileResolveScope(file)
      if (isWorksheetFile)
        WorksheetResolveFilterScope(defaultResolveScope, file)
      else
        ResolveFilterScope(defaultResolveScope)
    }
    else
      GlobalSearchScope.allScope(project)
  }


  override final def getUseScope: SearchScope =
    ScalaUseScope(super[PsiFileBase].getUseScope, this)

  protected def defaultFileResolveScope(file: VirtualFile): GlobalSearchScope =
    ResolveScopeManager.getInstance(getProject).getDefaultResolveScope(file)

  override def getPrevSibling: PsiElement = this.child match {
    case null => super.getPrevSibling
    case element => element.getPrevSibling
  }

  override def getNextSibling: PsiElement = this.child match {
    case null => super.getNextSibling
    case element => element.getNextSibling
  }

  override protected def insertFirstImport(importSt: ScImportStmt, first: PsiElement): PsiElement =
    super.insertFirstImport(importSt, first)

  override def typeDefinitions: Seq[ScTypeDefinition] = {
    val typeDefinitions = foldStub(findChildren[ScTypeDefinition]) { stub =>
      ArraySeq.unsafeWrapArray(stub.getChildrenByType(TYPE_DEFINITIONS, JavaArrayFactoryUtil.ScTypeDefinitionFactory))
    }

    typeDefinitions ++ packagings.flatMap(_.typeDefinitions)
  }

  override def members: Seq[ScMember] = {
    val members = foldStub(findChildren[ScMember]) { stub =>
      ArraySeq.unsafeWrapArray(stub.getChildrenByType(MEMBERS, JavaArrayFactoryUtil.ScMemberFactory))
    }

    members ++ packagings.flatMap(_.members)
  }

  private def foldStub[R](byPsi: => R)(byStub: ScFileStub => R): R = getStub match {
    case null => byPsi
    case stub => byStub(stub)
  }

  override def subtreeChanged(): Unit = {
    ModTracker.anyScalaPsiChange.incModificationCount()
    super.subtreeChanged()
  }

  override val allowsForwardReferences: Boolean = false

  override protected final def shouldNotProcessDefaultImport(fqn: String): Boolean = cachedInUserData("shouldNotProcessDefaultImport", this, ScalaPsiManager.instance(getProject).TopLevelModificationTracker, Tuple1(fqn)) {
    typeDefinitions match {
      case Seq(head) => head.qualifiedName == fqn
      case _         => false
    }
  }

  private var myContextModificationStamp: Long = 0

  override def getContextModificationStamp: Long =
    myContextModificationStamp

  override def incContextModificationStamp(): Unit =
    myContextModificationStamp += 1
}

object ScalaFileImpl {
  private val LOG = Logger.getInstance(getClass)
  private val QualifiedPackagePattern = "(.+)\\.(.+?)".r

  def pathIn(root: PsiElement): List[List[String]] =
    packagingsIn(root).map(packaging => toVector(packaging.packageName))

  private def packagingsIn(root: PsiElement): List[ScPackaging] = {
    root.children.findByType[ScPackaging] match {
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

  def performMoveRefactoring(body: => Unit): Unit = {
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
