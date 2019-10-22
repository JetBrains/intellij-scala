package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.{util => ju}

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.source.{PostprocessReformattingAspect, codeStyle}
import com.intellij.psi.impl.{DebugUtil, ResolveScopeManager}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.psi.api.FileDeclarationsHolder.DefaultImplicitlyImportedObjects
import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}

import scala.annotation.tailrec
import scala.collection.{JavaConverters, mutable}

class ScalaFileImpl(viewProvider: FileViewProvider,
                    override val getFileType: LanguageFileType = ScalaFileType.INSTANCE)
  extends PsiFileBase(viewProvider, getFileType.getLanguage)
    with ScalaFile
    with FileDeclarationsHolder
    with ScDeclarationSequenceHolder
    with ScControlFlowOwner
    with FileResolveScopeProvider {

  import ScalaFileImpl._
  import psi.stubs.ScFileStub
  import settings.ScalaProjectSettings

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitFile(this)
  }

  override def isCompiled: Boolean = false

  override def toString: String = "ScalaFile: " + getName

  protected final def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected final def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T =
    findChildByClass[T](clazz)

  override final def getName: String = super.getName

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    if (isScriptFile && !super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place))
      false
    else
      super.processDeclarations(processor, state, lastParent, place)

  private def isScriptFileImpl: Boolean = {
    val empty = this.children.forall {
      case _: PsiWhiteSpace => true
      case _: PsiComment => true
      case _ => false
    }
    if (empty) return true // treat empty or commented files as scripts to avoid project recompilations

    val childrenIterator = getNode.getChildren(null).iterator
    while (childrenIterator.hasNext) {
      val node = childrenIterator.next()
      node.getElementType match {
        case ScalaTokenTypes.tSH_COMMENT => return true
        case _ =>
          node.getPsi match {
            case _: ScPackaging => return false
            case _: ScValueOrVariable |
                 _: ScFunction |
                 _: ScTypeAlias |
                 _: ScExpression => return true
            case _ =>
          }
      }
    }

    false
  }

  @CachedInUserData(this, ModCount.anyScalaPsiModificationCount)
  override def isScriptFile: Boolean = getViewProvider match {
    case _: ScFileViewProvider =>
      foldStub(isScriptFileImpl)(Function.const(false))
    case _ => false
  }

  override def isMultipleDeclarationsAllowed: Boolean = false

  override def isWorksheetFile: Boolean =
    ScFile.VirtualFile.unapply(this).exists(worksheet.WorksheetFileType.isWorksheetFile(_)(getProject))

  def setPackageName(inName: String): Unit = {
    // TODO support multiple base packages simultaneously
    val basePackageName = {
      import JavaConverters._
      val basePackages = ScalaProjectSettings.getInstance(getProject).getBasePackages.asScala
      basePackages.find(inName.startsWith).getOrElse("")
    }

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

  def setPackageName(base: String, name: String) {
    if (packageName == null) return

    val vector = toVector(name)

    preservingClasses {
      val documentManager = PsiDocumentManager.getInstance(getProject)
      val document = documentManager.getDocument(this)

      val prefixText = this.children.instanceOf[ScPackaging]
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

  private def preservingClasses(block: => Unit) {
    val data = this.typeDefinitions

    block

    for ((aClass, oldClass) <- this.typeDefinitions.zip(data)) {
      codeStyle.CodeEditUtil.setNodeGenerated(oldClass.getNode, true)
      PostprocessReformattingAspect.getInstance(getProject).disablePostprocessFormattingInside {
        new Runnable {
          def run() {
            try {
              DebugUtil.startPsiModification(null)
              aClass.getNode.getTreeParent.replaceChild(aClass.getNode, oldClass.getNode)
            }
            finally {
              DebugUtil.finishPsiModification()
            }
          }
        }
      }
    }
  }

  private def stripPackagings(document: Document) {
    this.depthFirst().instanceOf[ScPackaging].foreach { p =>
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

  protected def packagings = foldStub(findChildrenByClassScala(classOf[ScPackaging])) {
    _.getChildrenByType(PACKAGING, JavaArrayFactoryUtil.ScPackagingFactory)
  }

  def getPackageName: String = packageName match {
    case null => ""
    case name => name
  }

  private def packageName: String = {
    if (isScriptFile || isWorksheetFile) return null

    @tailrec
    def inner(packagings: Seq[ScPackaging], result: StringBuilder): String =
      packagings match {
        case Seq() => if (result.isEmpty) "" else result.substring(1)
        case Seq(head) =>
          inner(head.packagings, result.append(".").append(head.packageName))
        case _ => null
      }

    inner(packagings, StringBuilder.newBuilder)
  }

  override def getClasses: Array[PsiClass] =
    if (isScriptFile || isWorksheetFile) PsiClass.EMPTY_ARRAY
    else {
      val definitions = this.typeDefinitions

      if (isDuringMoveRefactoring) definitions.toArray
      else {
        val arrayBuffer = mutable.ArrayBuffer.empty[PsiClass]
        for (definition <- definitions) {
          val toAdd = definition :: (definition match {
            case o: ScObject => o.fakeCompanionClass.toList
            case t: ScTrait =>
              t.fakeCompanionClass :: t.fakeCompanionModule.toList
            case c: ScClass => c.fakeCompanionModule.toList
            case _ => Nil
          })

          arrayBuffer ++= toAdd
        }
        arrayBuffer.toArray
      }
    }

  override def findReferenceAt(offset: Int): PsiReference = super.findReferenceAt(offset)

  override def controlFlowScope: Option[ScalaPsiElement] = Some(this)

  def getClassNames: ju.Set[String] = {
    import JavaConverters._
    typeDefinitions.toSet[ScTypeDefinition].flatMap { definition =>
      val classes = definition :: (definition match {
        case _: ScClass => Nil
        case scalaObject: ScObject => scalaObject.fakeCompanionClass.toList
        case scalaTrait: ScTrait => scalaTrait.fakeCompanionClass :: Nil
      })
      classes.map(_.getName)
    }.asJava
  }

  def packagingRanges: Seq[TextRange] =
    this.depthFirst().instancesOf[ScPackaging].flatMap(_.reference).map(_.getTextRange).toList

  def getFileResolveScope: GlobalSearchScope = getOriginalFile.getVirtualFile match {
    case file if file != null && file.isValid => defaultFileResolveScope(file)
    case _ => GlobalSearchScope.allScope(getProject)
  }

  protected def defaultFileResolveScope(file: VirtualFile): GlobalSearchScope =
    ResolveScopeManager.getInstance(getProject).getDefaultResolveScope(file)

  def ignoreReferencedElementAccessibility(): Boolean = true //todo: ?

  override def getPrevSibling: PsiElement = this.child match {
    case null => super.getPrevSibling
    case element => element.getPrevSibling
  }

  override def getNextSibling: PsiElement = this.child match {
    case null => super.getNextSibling
    case element => element.getNextSibling
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

  override def typeDefinitions: Seq[ScTypeDefinition] = {
    val typeDefinitions = foldStub(findChildrenByClassScala(classOf[ScTypeDefinition])) {
      _.getChildrenByType(TYPE_DEFINITIONS, JavaArrayFactoryUtil.ScTypeDefinitionFactory)
    }

    typeDefinitions ++ indirectTypeDefinitions
  }

  protected final def indirectTypeDefinitions: Seq[ScTypeDefinition] = packagings.flatMap(_.typeDefinitions)

  private def foldStub[R](byPsi: => R)(byStub: ScFileStub => R): R = getStub match {
    case null => byPsi
    case stub => byStub(stub)
  }

  override def subtreeChanged(): Unit = {
    ScalaPsiManager.AnyScalaPsiModificationTracker.incModificationCount()
    super.subtreeChanged()
  }

  override final def getUseScope: SearchScope = super[PsiFileBase].getUseScope

  override val allowsForwardReferences: Boolean = false

  @CachedInUserData(this, ScalaPsiManager.instance(getProject).TopLevelModificationTracker)
  protected final def isScalaPredefinedClass: Boolean = typeDefinitions match {
    case Seq(head) => DefaultImplicitlyImportedObjects(head.qualifiedName)
    case _ => false
  }
}

object ScalaFileImpl {
  private val LOG = Logger.getInstance(getClass)
  private val QualifiedPackagePattern = "(.+)\\.(.+?)".r

  def pathIn(root: PsiElement): List[List[String]] =
    packagingsIn(root).map(packaging => toVector(packaging.packageName))

  private def packagingsIn(root: PsiElement): List[ScPackaging] = {
    root.children.instanceOf[ScPackaging] match {
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
