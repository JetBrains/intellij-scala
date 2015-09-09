package org.jetbrains.plugins.scala
package debugger

import java.util
import java.util.Collections

import com.intellij.debugger.engine._
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.{MultiRequestPositionManager, NoDataException, PositionManager, SourcePosition}
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScMacroDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
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
import scala.util.Try

/**
 * @author ilyas
 */
class ScalaPositionManager(debugProcess: DebugProcess) extends PositionManager with MultiRequestPositionManager {
  private val refTypeToFileCache = mutable.WeakHashMap[ReferenceType, PsiFile]()

  def getDebugProcess = debugProcess

  debugProcess.addDebugProcessListener(new DebugProcessAdapter {
    override def processDetached(process: DebugProcess, closedByUser: Boolean): Unit = {
      isCompiledWithIndyLambdasCache.clear()
      refTypeToFileCache.clear()
    }
  })

  @Nullable
  def getSourcePosition(@Nullable location: Location): SourcePosition = {
    def calcLineIndex(location: Location): Int = {
      LOG.assertTrue(getDebugProcess != null)
      try {
        customLineNumber(location).getOrElse(location.lineNumber - 1)
      }
      catch {
        case e: InternalError => -1
      }
    }

    if (shouldSkip(location)) return null

    val position =
      for {
        loc <- location.toOption
        psiFile <- getPsiFileByReferenceType(getDebugProcess.getProject, loc.declaringType).toOption
        lineNumber = calcLineIndex(loc)
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

    checkScalaFile(position)
    val file = position.getFile

    def hasLocations(refType: ReferenceType, position: SourcePosition): Boolean = {
      try {
        if (!file.isPhysical) { //may be generated in compiling evaluator
        val generatedClassName = file.getUserData(ScalaCompilingEvaluator.classNameKey)
          generatedClassName != null && refType.name().contains(generatedClassName)
        }
        else locationsOfLine(refType, position).size > 0
      } catch {
        case _: NoDataException | _: AbsentInformationException | _: ClassNotPreparedException | _: ObjectCollectedException => false
      }
    }

    val possiblePositions = positionsOnLine(file, position.getLine)

    val exactClasses = ArrayBuffer[ReferenceType]()
    val namePatterns = mutable.Set[NamePattern]()
    inReadAction {
      val onTheLine = possiblePositions.map(findGeneratingClassParent)
      if (onTheLine.isEmpty) return Collections.emptyList()
      val nonLambdaParents = onTheLine.head.parentsInFile.filter(p => ScalaEvaluatorBuilderUtil.isGenerateNonAnonfunClass(p))

      val sourceImages = onTheLine ++ nonLambdaParents
      sourceImages.foreach {
        case null =>
        case td: ScTypeDefinition if !DebuggerUtil.isLocalClass(td) =>
          val qName = getSpecificNameForDebugger(td)
          if (qName != null)
            exactClasses ++= getDebugProcess.getVirtualMachineProxy.classesByName(qName).asScala
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
    def findCustomizedLocations(line: Int) = {
      val allLocations = refType.allLineLocations().asScala
      allLocations.filter { l =>
        val custom = customLineNumber(l)
        custom.isDefined && custom.contains(line - 1)
      }
  }

    checkScalaFile(position)

    try {
      val line: Int = position.getLine + 1
      val jvmLocations: util.List[Location] =
        if (getDebugProcess.getVirtualMachineProxy.versionHigher("1.4"))
          refType.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line)
        else refType.locationsOfLine(line)
      val nonCustomizedJvm = jvmLocations.asScala.filter(l => customLineNumber(l).isEmpty)
      val customized = findCustomizedLocations(line)
      (nonCustomizedJvm ++ customized).filter(!shouldSkip(_)).asJava
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

      getDebugProcess.getRequestsManager.createClassPrepareRequest(waitRequestor.get, qName.get)
    }

    checkScalaFile(position)

    val possiblePositions = inReadAction {
      positionsOnLine(position.getFile, position.getLine).map(SourcePosition.createFromElement)
    }
    possiblePositions.map(createPrepareRequest).asJava
  }

  private def checkScalaFile(@NotNull position: SourcePosition): Unit = {
    position.getFile match {
      case _: ScalaFile =>
      case _ => throw NoDataException.INSTANCE
    }
  }

  private def filterAllClasses(condition: ReferenceType => Boolean): Seq[ReferenceType] = {
    import scala.collection.JavaConverters._
    getDebugProcess.getVirtualMachineProxy.allClasses.asScala.filter(condition)
  }

  @Nullable
  private def findReferenceTypeSourceImage(@NotNull position: SourcePosition): PsiElement = {
    val element = nonWhitespaceElement(position)
    findGeneratingClassParent(element)
  }

  private def nonWhitespaceElement(@NotNull position: SourcePosition): PsiElement = {
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

  private def customLineNumber(location: Location): Option[Int] = {
    //scalac sometimes generates very strange line numbers for <init> method
    def lineForConstructor: Option[Int] = {
      val declType = location.declaringType()
      inReadAction {
        findPsiClassByReferenceType(declType) match {
          case Some(c) =>
            val containingFile = c.getContainingFile
            val linePosition = SourcePosition.createFromLine(containingFile, location.lineNumber() - 1)
            val elem = nonWhitespaceElement(linePosition)
            val parent = PsiTreeUtil.getParentOfType(elem, classOf[ScBlockStatement], classOf[ScEarlyDefinitions])
            if (parent != null && PsiTreeUtil.isAncestor(c, parent, false)) None
            else {
              val doc = PsiDocumentManager.getInstance(getDebugProcess.getProject).getDocument(containingFile)
              Some(doc.getLineNumber(c.getTextOffset))
            }
          case None => None
        }
      }
    }

    if (location.method.isConstructor && location.method.location == location) lineForConstructor
    else None
  }

  private def calcPosition(file: PsiFile, location: Location, lineNumber: Int): Option[SourcePosition] = {
    def calcElement(): Option[PsiElement] = {
      val possiblePositions = positionsOnLine(file, lineNumber)
      if (possiblePositions.size <= 1) return possiblePositions.headOption

      val currentMethod = location.method()
      val methodName = currentMethod.name()

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
        val generatingPsiElem = findPsiClassByReferenceType(declaringType)
        possiblePositions.find(p => generatingPsiElem.contains(findGeneratingClassParent(p)))
      }
    }

    calcElement().map(SourcePosition.createFromElement)
  }

  private def findScriptFile(refType: ReferenceType): Option[PsiFile] = {
    try {
      val name = refType.name()
      if (name.startsWith(SCRIPT_HOLDER_CLASS_NAME)) {
        val sourceName = refType.sourceName
        val files = FilenameIndex.getFilesByName(getDebugProcess.getProject, sourceName, getDebugProcess.getSearchScope)
        files.headOption
      }
      else None
    }
    catch {
      case e: AbsentInformationException => None
    }
  }

  private def findClassByQualName(qName: String): Option[PsiClass] = {
    val project = getDebugProcess.getProject
    val cacheManager = ScalaShortNamesCacheManager.getInstance(project)
    val packageSuffix = ".package$"
    val classes =
      if (qName.endsWith(packageSuffix))
        Seq(cacheManager.getPackageObjectByName(qName.stripSuffix(packageSuffix), GlobalSearchScope.allScope(project)))
      else
        cacheManager.getClassesByFQName(qName, getDebugProcess.getSearchScope)

    val clazz =
      if (classes.length == 1) classes.headOption
      else if (classes.length == 2 && ScalaPsiUtil.getCompanionModule(classes.head).contains(classes(1))) classes.headOption
      else None
    clazz.filter(_.isValid)
  }

  @Nullable
  private def getPsiFileByReferenceType(project: Project, refType: ReferenceType): PsiFile = {

    def qualName(refType: ReferenceType): String = {
      val originalQName = refType.name.replace('/', '.')
      if (originalQName.endsWith("package$")) return originalQName

      val dollar: Int = originalQName.indexOf('$')
      if (dollar >= 0) originalQName.substring(0, dollar) else originalQName
    }

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
        val qName = qualName(refType)

        if (!ScalaMacroDebuggingUtil.isEnabled)
          findClassByQualName(qName).map(_.getNavigationElement.getContainingFile).orNull
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

  private def findPsiClassByReferenceType(refType: ReferenceType): Option[PsiElement] = {
    val project = getDebugProcess.getProject

    val refTypeLineNumbers = refType.allLineLocations().asScala.map(_.lineNumber() - 1)
    if (refTypeLineNumbers.isEmpty) return None

    val firtsRefTypeLine = refTypeLineNumbers.min
    val lastRefTypeLine = refTypeLineNumbers.max
    val refTypeLines = firtsRefTypeLine to lastRefTypeLine

    val file = getPsiFileByReferenceType(project, refType)
    if (file == null) return None

    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document == null) return None

    val containerTry = Try {
      val firstOffset = document.getLineStartOffset(firtsRefTypeLine)
      val endOffset = document.getLineEndOffset(lastRefTypeLine)
      val startElem = file.findElementAt(firstOffset)
      val commonParent = startElem.parentsInFile.find(_.getTextRange.getEndOffset > endOffset)
      commonParent.flatMap(cp => Option(findGeneratingClassParent(cp)))
    }
    val container = containerTry.toOption.flatten.getOrElse(file)

    def elementLineRange(elem: PsiElement, document: Document) = {
      val startLine = document.getLineNumber(elem.getTextRange.getStartOffset)
      val endLine = document.getLineNumber(elem.getTextRange.getEndOffset)
      startLine to endLine
    }

    def checkLines(elem: PsiElement, document: Document) = {
      val lineRange = elementLineRange(elem, document)
      //intersection, very loose check because sometimes first line for <init> method is after range of the class
      firtsRefTypeLine <= lineRange.end && lastRefTypeLine >= lineRange.start
    }

    def findCandidates(): Seq[PsiElement] = {
      container.depthFirst.collect {
        case elem if ScalaEvaluatorBuilderUtil.isGenerateClass(elem) && checkLines(elem, document) && nameMatches(elem, refType) => elem
      }.toIndexedSeq
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

    val byContainingClasses = filteredWithSignature.groupBy(c => findGeneratingClassParent(c.getParent))
    if (byContainingClasses.size > 1) {
      findContainingClass(refType) match {
        case Some(e) => return byContainingClasses.get(e).flatMap(_.headOption)
        case None =>
      }
    }
    filteredWithSignature.headOption
  }

  private def findContainingClass(refType: ReferenceType): Option[PsiElement] = {
    def classesByName(s: String) = {
      val vm = getDebugProcess.getVirtualMachineProxy
      vm.classesByName(s).asScala
    }

    val name = refType.name()
    val index = name.lastIndexOf("$$")
    if (index < 0) return None

    val containingName = name.substring(0, index)
    classesByName(containingName).headOption.flatMap(findPsiClassByReferenceType)
  }
}

object ScalaPositionManager {
  private val LOG: Logger = Logger.getInstance("#com.intellij.debugger.engine.PositionManagerImpl")
  private val SCRIPT_HOLDER_CLASS_NAME: String = "Main$$anon$1"

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

  private def positionsOnLineInner(file: ScalaFile, lineNumber: Int): Seq[PsiElement] = {
    inReadAction {
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
      if (lineNumber >= document.getLineCount) return Seq.empty
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
        val parentsOnTheLine = element.parents.takeWhile(e => e.getTextOffset > startLine).toIndexedSeq
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
  def findGeneratingClassParent(element: PsiElement): PsiElement = {
    element match {
      case null => null
      case elem if ScalaEvaluatorBuilderUtil.isGenerateClass(elem) => elem
      case expr: ScExpression if isInsideMacro(element) => expr
      case elem => findGeneratingClassParent(elem.getParent)
    }
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

  private def shouldSkip(location: Location) = {
    val syntheticProvider = SyntheticTypeComponentProvider.EP_NAME.findExtension(classOf[ScalaSyntheticProvider])
    DebuggerSettings.getInstance().SKIP_SYNTHETIC_METHODS && syntheticProvider.isSynthetic(location.method())
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
    private val classJVMNameParts: Seq[String] = {
      val forElem = partsFor(elem).toIterator
      val forParents = elem.parentsInFile.flatMap(e => partsFor(e))
      (forElem ++ forParents).toSeq.reverse
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
      val containingClass = findGeneratingClassParent(elem.getParent)
      val owner = PsiTreeUtil.getParentOfType(elem, classOf[ScFunctionDefinition], classOf[ScTypeDefinition],
        classOf[ScPatternDefinition], classOf[ScVariableDefinition])
      val firstParts =
        if (PsiTreeUtil.isAncestor(owner, containingClass, true)) Seq("$anonfun")
        else owner match {
          case fun: ScFunctionDefinition => Seq(s"$$${fun.name}", "$anonfun")
          case _ => Seq("$anonfun")
        }
      lastParts ++ firstParts
    }

    def matches(refType: ReferenceType): Boolean = {
      val name = refType.name()

      def checkParts(): Boolean = {
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

      elem match {
        case td: ScTypeDefinition if !DebuggerUtil.isLocalClass(td) =>
          val qName = getSpecificNameForDebugger(td)
          name == qName
        case _ => checkParts()
      }
    }
  }

  private object NamePattern {
    def forElement(elem: PsiElement): NamePattern = {
      if (!ScalaEvaluatorBuilderUtil.isGenerateClass(elem)) return null

      val cacheProvider = new CachedValueProvider[NamePattern] {
        override def compute(): Result[NamePattern] = Result.create(new NamePattern(elem), elem)
      }

      CachedValuesManager.getCachedValue(elem, cacheProvider)
    }
  }
}
