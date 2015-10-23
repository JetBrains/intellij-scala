package org.jetbrains.plugins.scala
package debugger

import java.util
import java.util.Collections

import com.intellij.debugger.engine._
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.{MultiRequestPositionManager, NoDataException, PositionManager, SourcePosition}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiTreeUtil}
import com.intellij.util.{Processor, Query}
import com.sun.jdi._
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager._
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaCompilingEvaluator
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import org.jetbrains.plugins.scala.util.macroDebug.ScalaMacroDebuggingUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.NameTransformer
import scala.util.Try

/**
 * @author ilyas
 */
class ScalaPositionManager(val debugProcess: DebugProcess) extends PositionManager with MultiRequestPositionManager with LocationLineManager {

  protected val caches = ScalaPositionManagerCaches.instance(debugProcess)
  import caches._

  @Nullable
  def getSourcePosition(@Nullable location: Location): SourcePosition = {
    if (shouldSkip(location)) return null

    val position =
      for {
        loc <- location.toOption
        psiFile <- getPsiFileByReferenceType(debugProcess.getProject, loc.declaringType).toOption
        lineNumber = exactLineNumber(location)
        if lineNumber >= 0
      } yield {
        calcPosition(psiFile, location, lineNumber).getOrElse {
          SourcePosition.createFromLine(psiFile, lineNumber)
        }
      }
    position match {
      case Some(p) => p
      case None => throw NoDataException.INSTANCE
    }
  }

  @NotNull
  def getAllClasses(@NotNull position: SourcePosition): util.List[ReferenceType] = {

    val file = position.getFile
    throwIfNotScalaFile(file)

    def hasLocations(refType: ReferenceType, position: SourcePosition): Boolean = {
      try {
        val generatedClassName = file.getUserData(ScalaCompilingEvaluator.classNameKey)
        if (generatedClassName != null) refType.name().contains(generatedClassName)
        else locationsOfLine(refType, position).size > 0
      } catch {
        case _: NoDataException | _: AbsentInformationException | _: ClassNotPreparedException | _: ObjectCollectedException => false
      }
    }

    val possiblePositions = positionsOnLine(file, position.getLine)

    val exactClasses = ArrayBuffer[ReferenceType]()
    val namePatterns = mutable.Set[NamePattern]()
    inReadAction {
      val onTheLine = possiblePositions.map(findGeneratingClassOrMethodParent)
      if (onTheLine.isEmpty) return Collections.emptyList()
      val nonLambdaParent =
        if (isCompiledWithIndyLambdas(file))
          onTheLine.head.parentsInFile.find(p => ScalaEvaluatorBuilderUtil.isGenerateNonAnonfunClass(p))
        else None

      val sourceImages = onTheLine ++ nonLambdaParent
      sourceImages.foreach {
        case null =>
        case td: ScTypeDefinition if !DebuggerUtil.isLocalClass(td) =>
          val qName = getSpecificNameForDebugger(td)
          if (qName != null)
            exactClasses ++= debugProcess.getVirtualMachineProxy.classesByName(qName).asScala
        case elem =>
          val namePattern = NamePattern.forElement(elem)
          namePatterns ++= Option(namePattern)
      }
    }
    val foundWithPattern = filterAllClasses(c => namePatterns.exists(_.matches(c)) && hasLocations(c, position))
    (exactClasses ++ foundWithPattern).distinct.asJava
  }

  @NotNull
  def locationsOfLine(@NotNull refType: ReferenceType, @NotNull position: SourcePosition): util.List[Location] = {

    throwIfNotScalaFile(position.getFile)

    try {
      val line: Int = position.getLine
      locationsOfLine(refType, line).asJava
    }
    catch {
      case e: AbsentInformationException => Collections.emptyList()
    }
  }

  def createPrepareRequest(@NotNull requestor: ClassPrepareRequestor, @NotNull position: SourcePosition): ClassPrepareRequest = {
    throw new IllegalStateException("This class implements MultiRequestPositionManager, corresponding createPrepareRequests version should be used")
  }

  override def createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): util.List[ClassPrepareRequest] = {
    def isLocalOrUnderDelayedInit(definition: PsiClass): Boolean = {
      def isDelayed = definition match {
        case obj: ScObject =>
          val manager: ScalaPsiManager = ScalaPsiManager.instance(obj.getProject)
          val clazz: PsiClass = manager.getCachedClass(obj.getResolveScope, "scala.DelayedInit")
          clazz != null && manager.cachedDeepIsInheritor(obj, clazz)
        case _ => false
      }
      DebuggerUtil.isLocalClass(definition) || isDelayed
    }

    def findEnclosingTypeDefinition: Option[ScTypeDefinition] = {
      @tailrec
      def notLocalEnclosingTypeDefinition(element: PsiElement): Option[ScTypeDefinition] = {
        PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition]) match {
          case null => None
          case td if DebuggerUtil.isLocalClass(td) => notLocalEnclosingTypeDefinition(td.getParent)
          case td => Some(td)
        }
      }
      val element = nonWhitespaceElement(position)
      notLocalEnclosingTypeDefinition(element)
    }

    def createPrepareRequest(position: SourcePosition): ClassPrepareRequest = {
      val qName = new Ref[String](null)
      val waitRequestor = new Ref[ClassPrepareRequestor](null)
      inReadAction {
        val sourceImage = findReferenceTypeSourceImage(position)
        val insideMacro: Boolean = isInsideMacro(nonWhitespaceElement(position))
        sourceImage match {
          case cl: ScClass if ValueClassType.isValueClass(cl) =>
            //there are no instances of value classes, methods from companion object are used
            qName.set(getSpecificNameForDebugger(cl) + "$")
          case typeDef: ScTypeDefinition if !isLocalOrUnderDelayedInit(typeDef) =>
            val specificName = getSpecificNameForDebugger(typeDef)
            qName.set(if (insideMacro) specificName + "*" else specificName)
          case _ =>
            findEnclosingTypeDefinition.foreach(typeDef => qName.set(typeDef.getQualifiedNameForDebugger + "*"))
        }
        // Enclosing type definition is not found
        if (qName.get == null) {
          qName.set(SCRIPT_HOLDER_CLASS_NAME + "*")
        }
        waitRequestor.set(new ScalaPositionManager.MyClassPrepareRequestor(position, requestor))
      }

      debugProcess.getRequestsManager.createClassPrepareRequest(waitRequestor.get, qName.get)
    }

    val file = position.getFile
    throwIfNotScalaFile(file)

    val possiblePositions = inReadAction {
      positionsOnLine(file, position.getLine).map(SourcePosition.createFromElement)
    }
    possiblePositions.map(createPrepareRequest).asJava
  }

  private def throwIfNotScalaFile(file: PsiFile): Unit = {
    if (!checkScalaFile(file)) throw NoDataException.INSTANCE
  }

  private def checkScalaFile(file: PsiFile): Boolean = file match {
    case sf: ScalaFile => !sf.isCompiled
    case _ => false
  }

  private def filterAllClasses(condition: ReferenceType => Boolean): Seq[ReferenceType] = {
    import scala.collection.JavaConverters._
    debugProcess.getVirtualMachineProxy.allClasses.asScala.filter(condition)
  }

  @Nullable
  private def findReferenceTypeSourceImage(@NotNull position: SourcePosition): PsiElement = {
    val element = nonWhitespaceElement(position)
    findGeneratingClassOrMethodParent(element)
  }

  protected def nonWhitespaceElement(@NotNull position: SourcePosition): PsiElement = {
    val file = position.getFile
    @tailrec
    def nonWhitespaceInner(element: PsiElement, document: Document): PsiElement = {
      element match {
        case null => null
        case ws: PsiWhiteSpace if document.getLineNumber(element.getTextRange.getEndOffset) == position.getLine =>
          val nextElement = file.findElementAt(element.getTextRange.getEndOffset)
          nonWhitespaceInner(nextElement, document)
        case _ => element
      }
    }
    if (!file.isInstanceOf[ScalaFile]) null
    else {
      val firstElement = file.findElementAt(position.getOffset)
      try {
        val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
        nonWhitespaceInner(firstElement, document)
      }
      catch {
        case t: Throwable => firstElement
      }
    }
  }

  private def calcPosition(file: PsiFile, location: Location, lineNumber: Int): Option[SourcePosition] = {
    throwIfNotScalaFile(file)

    val currentMethod = location.method()
    if (!isAnonfun(currentMethod)) return Some(SourcePosition.createFromLine(file, lineNumber))

    def calcElement(): Option[PsiElement] = {
      val methodName = currentMethod.name()

      val possiblePositions = positionsOnLine(file, lineNumber)
      if (possiblePositions.size <= 1) return possiblePositions.headOption

      def findDefaultArg: Option[PsiElement] = {
        try {
          val (start, index) = methodName.splitAt(methodName.lastIndexOf("$") + 1)
          if (!start.endsWith("$default$")) return None

          val paramNumber = index.toInt - 1
          possiblePositions.find {
            case e =>
              val scParameters = PsiTreeUtil.getParentOfType(e, classOf[ScParameters])
              if (scParameters != null) {
                val param = scParameters.params(paramNumber)
                param.isDefaultParam && param.isAncestorOf(e)
              }
              else false
          }
        } catch {
          case e: Exception => None
        }
      }

      val declaringType = location.declaringType()

      def findPsiElementForIndyLambda(): Option[PsiElement] = {
        val lambdas = lambdasOnLine(file, lineNumber)
        val methods = indyLambdaMethodsOnLine(declaringType, lineNumber)
        val methodsToLambdas = methods.zip(lambdas).toMap
        methodsToLambdas.get(currentMethod)
      }

      if (methodName.contains("$default$")) {
        return findDefaultArg
      }

      if (isIndyLambda(currentMethod)) findPsiElementForIndyLambda()
      else {
        val generatingPsiElem = findElementByReferenceType(declaringType)
        possiblePositions.find(p => generatingPsiElem.contains(findGeneratingClassOrMethodParent(p)))
      }
    }

    calcElement().map(SourcePosition.createFromElement)
  }

  private def findScriptFile(refType: ReferenceType): Option[PsiFile] = {
    try {
      val name = refType.name()
      if (name.startsWith(SCRIPT_HOLDER_CLASS_NAME)) {
        val sourceName = refType.sourceName
        val files = FilenameIndex.getFilesByName(debugProcess.getProject, sourceName, debugProcess.getSearchScope)
        files.headOption
      }
      else None
    }
    catch {
      case e: AbsentInformationException => None
    }
  }

  @Nullable
  private def getPsiFileByReferenceType(project: Project, refType: ReferenceType): PsiFile = {

    def searchForMacroDebugging(qName: String): PsiFile = {
      val directoryIndex: DirectoryIndex = DirectoryIndex.getInstance(project)
      val dotIndex = qName.lastIndexOf(".")
      val packageName = if (dotIndex > 0) qName.substring(0, dotIndex) else ""
      val query: Query[VirtualFile] = directoryIndex.getDirectoriesByPackageName(packageName, true)
      val fileNameWithoutExtension = if (dotIndex > 0) qName.substring(dotIndex + 1) else qName
      val fileNames: util.Set[String] = new util.HashSet[String]
      import scala.collection.JavaConversions._
      for (extention <- ScalaLoader.SCALA_EXTENSIONS) {
        fileNames.add(fileNameWithoutExtension + "." + extention)
      }
      val result = new Ref[PsiFile]
      query.forEach(new Processor[VirtualFile] {
        override def process(vDir: VirtualFile): Boolean = {
          var isFound = false
          for {
            fileName <- fileNames
            if !isFound
            vFile <- vDir.findChild(fileName).toOption
          } {
            val psiFile: PsiFile = PsiManager.getInstance(project).findFile(vFile)
            val debugFile: PsiFile = ScalaMacroDebuggingUtil.loadCode(psiFile, force = false)
            if (debugFile != null) {
              result.set(debugFile)
              isFound = true
            }
            else if (psiFile.isInstanceOf[ScalaFile]) {
              result.set(psiFile)
              isFound = true
            }
          }
          !isFound
        }
      })
      result.get
    }

    if (refType == null) return null

    def findFile() = {
      val scriptFile = findScriptFile(refType)
      val file = scriptFile.getOrElse {
        val originalQName = NameTransformer.decode(refType.name)
        val qName =
          if (originalQName.endsWith(packageSuffix)) originalQName
          else originalQName.replace(packageSuffix, ".").takeWhile(_ != '$')

        if (!ScalaMacroDebuggingUtil.isEnabled)
          findClassByQualName(qName, originalQName.endsWith("$")).map(_.getNavigationElement.getContainingFile).orNull
        else
          searchForMacroDebugging(qName)
      }

      if (refType.methods().asScala.exists(isIndyLambda)) {
        isCompiledWithIndyLambdasCache.update(file, true)
      }

      file
    }

    refTypeToFileCache.getOrElseUpdate(refType, findFile())
  }

  private def nameMatches(elem: PsiElement, refType: ReferenceType): Boolean = {
    val pattern = NamePattern.forElement(elem)
    pattern != null && pattern.matches(refType)
  }

  def findElementByReferenceType(refType: ReferenceType): Option[PsiElement] = {
    def createPointer(elem: PsiElement) =
      SmartPointerManager.getInstance(debugProcess.getProject).createSmartPsiElementPointer(elem)

    refTypeToElementCache.get(refType) match {
      case Some(Some(p)) if p.getElement != null => Some(p.getElement)
      case Some(Some(_)) | None =>
        val found = findElementByReferenceTypeInner(refType)
        refTypeToElementCache.update(refType, found.map(createPointer))
        found
      case Some(None) => None
    }
  }

  private def findElementByReferenceTypeInner(refType: ReferenceType): Option[PsiElement] = {

    val byName = findByQualName(refType) orElse findByShortName(refType)
    if (byName.isDefined) return byName

    val project = debugProcess.getProject

    val allLocations = Try(refType.allLineLocations().asScala).getOrElse(Seq.empty)

    val refTypeLineNumbers = allLocations.map(checkedLineNumber).filter(_ > 0)
    if (refTypeLineNumbers.isEmpty) return None

    val firstRefTypeLine = refTypeLineNumbers.min
    val lastRefTypeLine = refTypeLineNumbers.max
    val refTypeLines = firstRefTypeLine to lastRefTypeLine

    val file = getPsiFileByReferenceType(project, refType)
    if (!checkScalaFile(file)) return None

    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document == null) return None

    def elementLineRange(elem: PsiElement, document: Document) = {
      val startLine = document.getLineNumber(elem.getTextRange.getStartOffset)
      val endLine = document.getLineNumber(elem.getTextRange.getEndOffset)
      startLine to endLine
    }

    def checkLines(elem: PsiElement, document: Document) = {
      val lineRange = elementLineRange(elem, document)
      //intersection, very loose check because sometimes first line for <init> method is after range of the class
      firstRefTypeLine <= lineRange.end && lastRefTypeLine >= lineRange.start
    }

    def isAppropriateCandidate(elem: PsiElement) = {
      checkLines(elem, document) && ScalaEvaluatorBuilderUtil.isGenerateClass(elem) && nameMatches(elem, refType)
    }

    def findCandidates(): Seq[PsiElement] = {
      if (lastRefTypeLine - firstRefTypeLine >= 2) {
        val offsetInTheMiddle = document.getLineEndOffset(firstRefTypeLine + 1)
        val startElem = file.findElementAt(offsetInTheMiddle)
        val res = startElem.parentsInFile.find(isAppropriateCandidate).toSeq
        res
      }
      else {
        val firstLinePositions = positionsOnLine(file, firstRefTypeLine)
        val allPositions =
          if (firstRefTypeLine == lastRefTypeLine) firstLinePositions
          else firstLinePositions ++ positionsOnLine(file, lastRefTypeLine)
        val res = allPositions.distinct.filter(isAppropriateCandidate)
        res
      }
    }

    def filterWithSignature(candidates: Seq[PsiElement]) = {
      val applySignature = refType.methodsByName("apply").asScala.find(m => !m.isSynthetic).map(_.signature())
      if (applySignature.isEmpty) candidates
      else {
        candidates.filter(l => applySignature == DebuggerUtil.lambdaJVMSignature(l))
      }
    }

    val candidates = findCandidates()

    if (candidates.size <= 1) return candidates.headOption

    if (refTypeLines.size > 1) {
      val withExactlySameLines = candidates.filter(elementLineRange(_, document) == refTypeLines)
      if (withExactlySameLines.size == 1) return withExactlySameLines.headOption
    }

    if (candidates.exists(!isLambda(_))) return candidates.headOption

    val filteredWithSignature = filterWithSignature(candidates)
    if (filteredWithSignature.size == 1) return filteredWithSignature.headOption

    val byContainingClasses = filteredWithSignature.groupBy(c => findGeneratingClassOrMethodParent(c.getParent))
    if (byContainingClasses.size > 1) {
      findContainingClass(refType) match {
        case Some(e) => return byContainingClasses.get(e).flatMap(_.headOption)
        case None =>
      }
    }
    filteredWithSignature.headOption
  }

  private def findClassByQualName(qName: String, isScalaObject: Boolean): Option[PsiClass] = {
    val project = debugProcess.getProject

    val cacheManager = ScalaShortNamesCacheManager.getInstance(project)
    val classes =
      if (qName.endsWith(packageSuffix))
        Seq(cacheManager.getPackageObjectByName(qName.stripSuffix(packageSuffix), GlobalSearchScope.allScope(project)))
      else
        cacheManager.getClassesByFQName(qName.replace(packageSuffix, "."), debugProcess.getSearchScope)

    val clazz =
      if (classes.length == 1) classes.headOption
      else if (classes.length >= 2) {
        if (isScalaObject) classes.find(_.isInstanceOf[ScObject])
        else classes.find(!_.isInstanceOf[ScObject])
      }
      else None
    clazz.filter(_.isValid)
  }

  private def findByQualName(refType: ReferenceType): Option[PsiClass] = {
    val originalQName = NameTransformer.decode(refType.name)
    val endsWithPackageSuffix = originalQName.endsWith(packageSuffix)
    val withoutSuffix =
      if (endsWithPackageSuffix) originalQName.stripSuffix(packageSuffix)
      else originalQName.stripSuffix("$").stripSuffix("$class")
    val withDots = withoutSuffix.replace(packageSuffix, ".").replace('$', '.')
    val transformed = if (endsWithPackageSuffix) withDots + packageSuffix else withDots

    findClassByQualName(transformed, originalQName.endsWith("$"))
  }

  private def findByShortName(refType: ReferenceType): Option[PsiClass] = {
    val project = debugProcess.getProject

    val file = getPsiFileByReferenceType(project, refType)
    lazy val sourceName = Try(refType.sourceName()).getOrElse("")

    def checkFile(elem: PsiElement) = {
      val containingFile = elem.getContainingFile
      if (file != null) containingFile == file
      else containingFile != null && containingFile.name == sourceName
    }

    val originalQName = NameTransformer.decode(refType.name)
    val withoutSuffix =
      if (originalQName.endsWith(packageSuffix)) originalQName
      else originalQName.replace(packageSuffix, ".").stripSuffix("$").stripSuffix("$class")
    val lastDollar = withoutSuffix.lastIndexOf('$')
    val lastDot = withoutSuffix.lastIndexOf('.')
    val index = Seq(lastDollar, lastDot, 0).max + 1
    val name = withoutSuffix.drop(index)
    val isScalaObject = originalQName.endsWith("$")

    val cacheManager = ScalaShortNamesCacheManager.getInstance(project)
    val classes = cacheManager.getClassesByName(name, GlobalSearchScope.allScope(project))

    val inSameFile = classes.filter(checkFile)
    val clazz =
      if (inSameFile.length == 1) classes.headOption
      else if (inSameFile.length >= 2) {
        if (isScalaObject) inSameFile.find(_.isInstanceOf[ScObject])
        else inSameFile.find(!_.isInstanceOf[ScObject])
      }
      else None
    clazz.filter(_.isValid)
  }

  private def findContainingClass(refType: ReferenceType): Option[PsiElement] = {
    def classesByName(s: String) = {
      val vm = debugProcess.getVirtualMachineProxy
      vm.classesByName(s).asScala
    }

    val name = NameTransformer.decode(refType.name())
    val index = name.lastIndexOf("$$")
    if (index < 0) return None

    val containingName = NameTransformer.encode(name.substring(0, index))
    classesByName(containingName).headOption.flatMap(findElementByReferenceType)
  }
}

object ScalaPositionManager {
  private val SCRIPT_HOLDER_CLASS_NAME: String = "Main$$anon$1"
  private val packageSuffix = ".package$"


  private val isCompiledWithIndyLambdasCache = mutable.HashMap[PsiFile, Boolean]()

  def positionsOnLine(file: PsiFile, lineNumber: Int): Seq[PsiElement] = {
    val scFile = file match {
      case sf: ScalaFile => sf
      case _ => return Seq.empty
    }
    val cacheProvider = new CachedValueProvider[mutable.HashMap[Int, Seq[PsiElement]]] {
      override def compute(): Result[mutable.HashMap[Int, Seq[PsiElement]]] = Result.create(mutable.HashMap[Int, Seq[PsiElement]](), file)
    }

    CachedValuesManager.getCachedValue(file, cacheProvider).getOrElseUpdate(lineNumber, positionsOnLineInner(scFile, lineNumber))
  }

  def checkedLineNumber(location: Location): Int =
    try location.lineNumber() - 1
    catch {case ie: InternalError => -1}

  private def positionsOnLineInner(file: ScalaFile, lineNumber: Int): Seq[PsiElement] = {
    inReadAction {
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
      if (document == null || lineNumber >= document.getLineCount) return Seq.empty
      val startLine = document.getLineStartOffset(lineNumber)
      val endLine = document.getLineEndOffset(lineNumber)

      def elementsOnTheLine(file: ScalaFile, lineNumber: Int): Seq[PsiElement] = {
        val result = ArrayBuffer[PsiElement]()
        var elem = file.findElementAt(startLine)

        while (elem != null && elem.getTextOffset <= endLine) {
          elem match {
            case ChildOf(_: ScUnitExpr) | ChildOf(ScBlock()) =>
              result += elem
            case ElementType(t) if ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(t) ||
                ScalaTokenTypes.BRACES_TOKEN_SET.contains(t) =>
            case _ =>
              result += elem
          }
          elem = PsiTreeUtil.nextLeaf(elem, true)
        }
        result
      }

      def findParent(element: PsiElement): Option[PsiElement] = {
        val parentsOnTheLine = element.parentsInFile.takeWhile(e => e.getTextOffset > startLine).toIndexedSeq
        val lambda = parentsOnTheLine.find(isLambda)
        val maxExpressionPatternOrTypeDef = parentsOnTheLine.reverse.find {
          case _: ScExpression => true
          case _: ScConstructorPattern | _: ScInfixPattern | _: ScBindingPattern => true
          case _: ScTypeDefinition => true
          case _ => false
        }
        Seq(lambda, maxExpressionPatternOrTypeDef).flatten.sortBy(_.getTextLength).headOption
      }
      elementsOnTheLine(file, lineNumber).flatMap(findParent).distinct
    }
  }

  def isLambda(element: PsiElement) = ScalaEvaluatorBuilderUtil.isGenerateAnonfun(element)

  def lambdasOnLine(file: PsiFile, lineNumber: Int): Seq[PsiElement] = {
    positionsOnLine(file, lineNumber).filter(isLambda)
  }

  def isIndyLambda(m: Method): Boolean = {
    val name = m.name()
    val lastDollar = name.lastIndexOf('$')
    lastDollar > 0 && name.substring(0, lastDollar).endsWith("$anonfun")
  }

  def isAnonfun(m: Method): Boolean = {
    def isAnonfunType(refType: ReferenceType) = {
      val name = NameTransformer.decode(refType.name())
      val separator = "$$"
      val index = name.lastIndexOf(separator)
      if (index < 0) false
      else {
        val lastPart = name.substring(index + separator.length)
        lastPart.startsWith("anonfun")
      }
    }
    isIndyLambda(m) || m.name.startsWith("apply") && isAnonfunType(m.declaringType())
  }

  def indyLambdaMethodsOnLine(refType: ReferenceType, lineNumber: Int): Seq[Method] = {
    def ordinal(m: Method) = {
      val name = m.name()
      val lastDollar = name.lastIndexOf('$')
      Try(name.substring(lastDollar + 1).toInt).getOrElse(-1)
    }

    val all = refType.methods().asScala.filter(isIndyLambda)
    val onLine = all.filter(m => Try(!m.locationsOfLine(lineNumber + 1).isEmpty).getOrElse(false))
    onLine.sortBy(ordinal)
  }

  def isCompiledWithIndyLambdas(file: PsiFile) = isCompiledWithIndyLambdasCache.getOrElse(file, false)

  @tailrec
  def findGeneratingClassOrMethodParent(element: PsiElement): PsiElement = {
    element match {
      case null => null
      case elem if ScalaEvaluatorBuilderUtil.isGenerateClass(elem) || isLambda(elem) => elem
      case expr: ScExpression if isInsideMacro(element) => expr
      case elem => findGeneratingClassOrMethodParent(elem.getParent)
    }
  }

  def shouldSkip(location: Location, debugProcess: DebugProcess) = {
    new ScalaPositionManager(debugProcess).shouldSkip(location)
  }

  private def isInsideMacro(element: PsiElement): Boolean = {
    var call = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall])
    while (call != null) {
      call.getEffectiveInvokedExpr match {
        case resRef: ResolvableReferenceElement =>
          if (resRef.resolve().isInstanceOf[ScMacroDefinition]) return true
        case _ =>
      }
      call = PsiTreeUtil.getParentOfType(call, classOf[ScMethodCall])
    }
    false
  }

  private def getSpecificNameForDebugger(td: ScTypeDefinition): String = {
    val name = td.getQualifiedNameForDebugger

    td match {
      case _: ScObject => s"$name$$"
      case _: ScTrait => s"$name$$class"
      case _ => name
    }
  }

  private class MyClassPrepareRequestor(position: SourcePosition, requestor: ClassPrepareRequestor) extends ClassPrepareRequestor {
   private val sourceFile = position.getFile
   private val sourceName = sourceFile.getName
   private def sourceNameOf(refType: ReferenceType): Option[String] = Try(refType.sourceName()).toOption

   def processClassPrepare(debuggerProcess: DebugProcess, referenceType: ReferenceType) {
     val positionManager: CompoundPositionManager = debuggerProcess.asInstanceOf[DebugProcessImpl].getPositionManager

     if (!sourceNameOf(referenceType).contains(sourceName)) return

      if (positionManager.locationsOfLine(referenceType, position).size > 0) {
        requestor.processClassPrepare(debuggerProcess, referenceType)
      }
      else {
        val positionClasses: util.List[ReferenceType] = positionManager.getAllClasses(position)
        if (positionClasses.contains(referenceType)) {
          requestor.processClassPrepare(debuggerProcess, referenceType)
        }
      }
    }
  }

  private class NamePattern(elem: PsiElement) {
    private val sourceName = elem.getContainingFile.getName
    private val isGeneratedForCompilingEvaluator = elem.getContainingFile.getUserData(ScalaCompilingEvaluator.classNameKey) != null
    private val exactName: Option[String] = {
      elem match {
        case td: ScTypeDefinition if !DebuggerUtil.isLocalClass(td) =>
          Some(getSpecificNameForDebugger(td))
        case _ => None
      }
    }
    private val classJVMNameParts: Seq[String] = {
      if (exactName.isDefined) Seq.empty
      else {
        val forElem = partsFor(elem).toIterator
        val forParents = elem.parentsInFile.flatMap(e => partsFor(e))
        (forElem ++ forParents).toSeq.reverse
      }
    }

    private def partsFor(elem: PsiElement): Seq[String] = {
      elem match {
        case e if ScalaEvaluatorBuilderUtil.isGenerateAnonfun(e) => partsForAnonfun(e)
        case newTd: ScNewTemplateDefinition if DebuggerUtil.generatesAnonClass(newTd) => Seq("$anon")
        case td: ScTypeDefinition => Seq(ScalaNamesUtil.toJavaName(td.name))
        case _ => Seq.empty
      }
    }

    private def partsForAnonfun(elem: PsiElement): Seq[String] = {
      val anonfunCount = ScalaEvaluatorBuilderUtil.anonClassCount(elem)
      val lastParts = Seq.fill(anonfunCount - 1)(Seq("$apply", "$anonfun")).flatten
      val containingClass = findGeneratingClassOrMethodParent(elem.getParent)
      val owner = PsiTreeUtil.getParentOfType(elem, classOf[ScFunctionDefinition], classOf[ScTypeDefinition],
        classOf[ScPatternDefinition], classOf[ScVariableDefinition])
      val firstParts =
        if (PsiTreeUtil.isAncestor(owner, containingClass, true)) Seq("$anonfun")
        else owner match {
          case fun: ScFunctionDefinition =>
            val name = if (fun.name == "this") JVMNameUtil.CONSTRUCTOR_NAME else fun.name
            val encoded = NameTransformer.encode(name)
            Seq(s"$$$encoded", "$anonfun")
          case _ => Seq("$anonfun")
        }
      lastParts ++ firstParts
    }

    private def checkParts(name: String): Boolean = {
      var nameTail = name
      for (part <- classJVMNameParts) {
        val index = nameTail.indexOf(part)
        if (index >= 0) {
          nameTail = nameTail.substring(index + part.length)
        }
        else return false
      }
      nameTail.indexOf("$anon") == -1
    }

    def matches(refType: ReferenceType): Boolean = {
      val refTypeSourceName = Try(refType.sourceName()).getOrElse("")
      if (refTypeSourceName != sourceName && !isGeneratedForCompilingEvaluator) return false

      val name = refType.name()

      exactName match {
        case Some(qName) => qName == name
        case None => checkParts(name)
      }
    }
  }

  private object NamePattern {
    def forElement(elem: PsiElement): NamePattern = {
      if (elem == null || !ScalaEvaluatorBuilderUtil.isGenerateClass(elem)) return null

      val cacheProvider = new CachedValueProvider[NamePattern] {
        override def compute(): Result[NamePattern] = Result.create(new NamePattern(elem), elem)
      }

      CachedValuesManager.getCachedValue(elem, cacheProvider)
    }
  }

  private[debugger] class ScalaPositionManagerCaches(debugProcess: DebugProcess) {

    debugProcess.addDebugProcessListener(new DebugProcessAdapter {
      override def processDetached(process: DebugProcess, closedByUser: Boolean): Unit = {
        clear()
      }
    })

    val refTypeToFileCache = mutable.WeakHashMap[ReferenceType, PsiFile]()
    val refTypeToElementCache = mutable.WeakHashMap[ReferenceType, Option[SmartPsiElementPointer[PsiElement]]]()

    val customizedLocationsCache = mutable.WeakHashMap[Location, Int]()
    val lineToCustomizedLocationCache = mutable.WeakHashMap[(ReferenceType, Int), Seq[Location]]()
    val seenRefTypes = mutable.Set[ReferenceType]()

    def clear(): Unit = {
      ScalaPositionManagerCaches.isCompiledWithIndyLambdasCache.clear()

      refTypeToFileCache.clear()
      refTypeToElementCache.clear()

      customizedLocationsCache.clear()
      lineToCustomizedLocationCache.clear()
      seenRefTypes.clear()
    }
  }

  private[debugger] object ScalaPositionManagerCaches {
    private val cachesMap = mutable.WeakHashMap[DebugProcess, ScalaPositionManagerCaches]()

    private val isCompiledWithIndyLambdasCache = mutable.HashSet[PsiFile]()

    def instance(debugProcess: DebugProcess) = {
      cachesMap.getOrElseUpdate(debugProcess, new ScalaPositionManagerCaches(debugProcess))
    }
  }

}