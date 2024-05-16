package org.jetbrains.plugins.scala
package debugger

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.debugger.engine._
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.{StackFrameProxyImpl, VirtualMachineProxyImpl}
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.debugger.{MultiRequestPositionManager, NoDataException, SourcePosition}
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry, LanguageFileType}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiTreeUtil}
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ConcurrentIntObjectMap
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi._
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.caches.{ScalaShortNamesCacheManager, cachedInUserData}
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager._
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator.ScalaCompilingEvaluator
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isLocalClass
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.util.AnonymousFunction._
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants._
import org.jetbrains.plugins.scala.util.TopLevelMembers.{findFileWithTopLevelMembers, topLevelMemberClassName}

import java.{util => ju}
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.reflect.NameTransformer
import scala.util.Try

class ScalaPositionManager(val debugProcess: DebugProcess) extends PositionManagerEx with MultiRequestPositionManager with LocationLineManager {

  protected[debugger] val caches = new ScalaPositionManagerCaches(debugProcess)
  private val outerAndNestedTypePartsPattern = """([^\$]*)(\$.*)?""".r
  import caches._

  private val debugProcessScope: ElementScope = ElementScope(debugProcess.getProject, debugProcess.getSearchScope)

  ScalaPositionManager.cacheInstance(this)

  override def getAcceptedFileTypes: ju.Set[_ <: FileType] =
    ju.Collections.singleton(ScalaFileType.INSTANCE)

  @Nullable
  override def getSourcePosition(@Nullable location: Location): SourcePosition = {
    if (debugProcess.getProject.isDisposed || shouldSkip(location)) return null

    val position =
      for {
        loc <- location.toOption
        psiFile <- getPsiFileByReferenceType(loc.declaringType).toOption
        lineNumber = exactLineNumber(location)
        if lineNumber >= 0
      } yield {
        calcPosition(psiFile, location, lineNumber).getOrElse {
          SourcePosition.createFromLine(psiFile, lineNumber)
        }
      }
    position.getOrThrow(NoDataException.INSTANCE)
  }

  @NotNull
  override def getAllClasses(@NotNull position: SourcePosition): ju.List[ReferenceType] = {

    val file = position.getFile
    throwIfNotScalaFile(file)

    val generatedClassName = file.getUserData(ScalaCompilingEvaluator.classNameKey)

    def hasLocations(refType: ReferenceType, position: SourcePosition): Boolean = {
      try {
        val generated = isGeneratedClass(generatedClassName, refType)
        lazy val sameFile = getPsiFileByReferenceType(refType) == file

        generated || sameFile && locationsOfLine(refType, position).size > 0
      } catch {
        case _: NoDataException | _: AbsentInformationException | _: ClassNotPreparedException | _: ObjectCollectedException => false
      }
    }

    val possiblePositions = positionsOnLine(file, position.getLine)
    val exactClasses = mutable.ArrayBuffer.empty[ReferenceType]
    val namePatterns = mutable.Set[NamePattern]()

    @RequiresReadLock
    def computeClassesPatternsAndPackageName(): Either[Unit, Option[String]] = {
      val packageName = possiblePositions.headOption.flatMap(findPackageName)

      val onTheLine = possiblePositions.map { e =>
        ProgressManager.checkCanceled()
        findGeneratingClassOrMethodParent(e)
      }
      if (onTheLine.isEmpty) return Left(())

      val nonLambdaParent =
        if (isCompiledWithIndyLambdas(file)) {
          val nonStrictParents = onTheLine.head.withParentsInFile
          nonStrictParents.find { p =>
            ProgressManager.checkCanceled()
            isGenerateNonAnonfunClass(p)
          }
        } else None

      def addExactClasses(td: ScTypeDefinition): Unit = {
        val qName = getSpecificNameForDebugger(td)
        val additional = td match {
          case _: ScTrait =>
            qName.stripSuffix(TraitImplementationClassSuffix_211) :: Nil
          case c: ScClass if ValueClassType.isValueClass(c) =>
            s"$qName$$" :: Nil
          case c if isDelayedInit(c) =>
            s"$qName$delayedInitBody" :: Nil
          case _ => Nil
        }
        (qName :: additional).foreach { name =>
          ProgressManager.checkCanceled()
          exactClasses ++= debugProcess.getVirtualMachineProxy.classesByName(name).asScala
        }
      }

      val sourceImages = onTheLine ++ nonLambdaParent
      sourceImages.foreach {
        case null =>
        case td: ScTypeDefinition if !isLocalClass(td) =>
          addExactClasses(td)
        case elem =>
          val namePattern = NamePattern.forElement(elem)
          namePatterns ++= Option(namePattern)
      }

      Right(packageName)
    }

    ReadAction.nonBlocking[Either[Unit, Option[String]]](() => computeClassesPatternsAndPackageName())
      .expireWhen(() => debugProcess.getProject.isDisposed)
      .executeSynchronously() match {
        case Left(()) => ju.Collections.emptyList()
        case Right(packageName) =>
          val foundWithPattern =
            if (namePatterns.isEmpty) Nil
            else filterAllClasses(c => hasLocations(c, position) && namePatterns.exists(_.matches(c)), packageName)
          val distinctExactClasses = exactClasses.distinct
          val loadedNestedClasses = getNestedClasses(distinctExactClasses).filter(hasLocations(_, position))

          (distinctExactClasses ++ foundWithPattern ++ loadedNestedClasses).distinct.asJava
      }
  }

  @NotNull
  override def locationsOfLine(@NotNull refType: ReferenceType, @NotNull position: SourcePosition): ju.List[Location] = {
    throwIfNotScalaFile(position.getFile)
    checkForIndyLambdas(refType)

    try {
      val line: Int = position.getLine
      locationsOfLine(refType, line).asJava
    } catch {
      case _: AbsentInformationException => ju.Collections.emptyList()
    }
  }

  override def evaluateCondition(context: EvaluationContext, frame: StackFrameProxyImpl, location: Location, expression: String): ThreeState =
    ThreeState.UNSURE

  override def createStackFrame(descriptor: StackFrameDescriptorImpl): XStackFrame =
    Option(descriptor.getLocation)
      .filter(isInScalaFile)
      .map(_ => new ScalaStackFrame(descriptor, true))
      .orNull

  private def isInScalaFile(location: Location): Boolean = {
    val refType = location.declaringType()
    try {
      val safeName = refType.sourceName()
      FileTypeRegistry.getInstance().getFileTypeByFileName(safeName) match {
        case lft: LanguageFileType if lft.getLanguage == ScalaLanguage.INSTANCE => true
        case _ => false
      }
    } catch {
      case _: AbsentInformationException => false
    }
  }

  override def createPrepareRequest(@NotNull requestor: ClassPrepareRequestor, @NotNull position: SourcePosition): ClassPrepareRequest = {
    throw new IllegalStateException("This class implements MultiRequestPositionManager, corresponding createPrepareRequests version should be used")
  }

  override def createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): ju.List[ClassPrepareRequest] = {
    def isLocalOrUnderDelayedInit(definition: PsiClass): Boolean = {
      isLocalClass(definition) || isDelayedInit(definition)
    }

    def findTopmostEnclosingTypeDefinition: Option[ScTypeDefinition] = {
      @tailrec
      def notLocalEnclosingTypeDefinition(element: PsiElement): Option[ScTypeDefinition] = {
        PsiTreeUtil.getTopmostParentOfType(element, classOf[ScTypeDefinition]) match {
          case null => None
          case td if isLocalClass(td) => notLocalEnclosingTypeDefinition(td.getParent)
          case td => Some(td)
        }
      }
      val element = nonWhitespaceElement(position)
      notLocalEnclosingTypeDefinition(element)
    }

    def findEnclosingPackageOrFile: Option[Either[ScPackaging, ScalaFile]] =
      nonWhitespaceElement(position).parentOfType(Seq(classOf[ScPackaging], classOf[ScalaFile]))
        .map {
          case pkg: ScPackaging => Left(pkg)
          case file: ScalaFile => Right(file)
        }

    def createClassPrepareRequests(classPrepareRequestor: ClassPrepareRequestor,
                                   classPreparePattern: String): Seq[ClassPrepareRequest] = {
      val reqManager = debugProcess.getRequestsManager
      val patternCoversNestedTypes = classPreparePattern.endsWith("*")
      if (patternCoversNestedTypes) {
        List(reqManager.createClassPrepareRequest(classPrepareRequestor, classPreparePattern))
      } else {
        val nestedTypesSuffix = if (classPreparePattern.endsWith("$")) "*" else "$*"
        val nestedTypesPattern = classPreparePattern + nestedTypesSuffix
        List(reqManager.createClassPrepareRequest(classPrepareRequestor, classPreparePattern),
          reqManager.createClassPrepareRequest(classPrepareRequestor, nestedTypesPattern))
      }
    }

    @RequiresReadLock
    def computeClassPattern(position: SourcePosition): ClassPattern = {
      val sourceImage = findReferenceTypeSourceImage(position)
      sourceImage match {
        case cl: ScClass if ValueClassType.isValueClass(cl) =>
          //there are no instances of value classes, methods from companion object are used
          ClassPattern.Single(getSpecificNameForDebugger(cl) + "$")
        case tr: ScTrait if !isLocalClass(tr) =>
          //to handle both trait methods encoding
          ClassPattern.Nested(tr.getQualifiedNameForDebugger)
        case typeDef: ScTypeDefinition if !isLocalOrUnderDelayedInit(typeDef) =>
          val specificName = getSpecificNameForDebugger(typeDef)
          val insideMacro = isInsideMacro(nonWhitespaceElement(position))
          if (insideMacro) ClassPattern.Nested(specificName) else ClassPattern.Single(specificName)
        case file: ScalaFile =>
          //top level member in default package
          ClassPattern.Single(topLevelMemberClassName(file, None))
        case pckg: ScPackaging =>
          ClassPattern.Single(topLevelMemberClassName(pckg.getContainingFile, Some(pckg)))
        case _ =>
          val pattern =
            findTopmostEnclosingTypeDefinition match {
              case Some(td) => Some(ClassPattern.Nested(getSpecificNameForDebugger(td)))
              case None =>
                findEnclosingPackageOrFile.map {
                  case Left(pkg) => ClassPattern.Single(topLevelMemberClassName(pkg.getContainingFile, Some(pkg)))
                  case Right(file) => ClassPattern.Single(topLevelMemberClassName(file, None))
                }
            }
          pattern.getOrElse(ClassPattern.Nested(SCRIPT_HOLDER_CLASS_NAME))
      }
    }

    def createRequestor(position: SourcePosition): MyClassPrepareRequestor =
      new MyClassPrepareRequestor(position, requestor)

    val file = position.getFile
    throwIfNotScalaFile(file)

    val onLine = positionsOnLine(file, position.getLine)
    val patterns = ReadAction.nonBlocking[Seq[(SourcePosition, ClassPattern)]](() => {
      onLine.map { e =>
        ProgressManager.checkCanceled()
        val position = SourcePosition.createFromElement(e)
        (position, computeClassPattern(position))
      }
    }).expireWhen(() => debugProcess.getProject.isDisposed).executeSynchronously()

    val (nested, single) = patterns.distinctBy(_._2).partitionMap {
      case (position, ClassPattern.Nested(pattern)) => Left(position -> ClassPattern.Nested(pattern))
      case (position, ClassPattern.Single(pattern)) => Right(position -> ClassPattern.Single(pattern))
    }
    val singleFiltered = {
      val nestedStrings = nested.map { case (_, ClassPattern.Nested(p)) => p }.toSet
      single.filterNot { case (_, ClassPattern.Single(p)) => nestedStrings.contains(p) }
    }
    val filteredPatterns = nested ++ singleFiltered

    val requestorsAndPatterns = filteredPatterns.map {
      case (position, ClassPattern.Nested(pattern)) => (createRequestor(position), pattern + "*")
      case (position, ClassPattern.Single(pattern)) => (createRequestor(position), pattern)
    }

    requestorsAndPatterns.flatMap { case (requestor, pattern) => createClassPrepareRequests(requestor, pattern) }.asJava
  }

  private def throwIfNotScalaFile(file: PsiFile): Unit = {
    if (!checkScalaFile(file)) throw NoDataException.INSTANCE
  }

  private def checkScalaFile(file: PsiFile): Boolean = file match {
    case sf: ScalaFile => !sf.isCompiled
    case _ => false
  }

  private def filterAllClasses(condition: ReferenceType => Boolean, packageName: Option[String]): collection.Seq[ReferenceType] = {
    def samePackage(refType: ReferenceType) = {
      val name = nonLambdaName(refType)
      val lastDot = name.lastIndexOf('.')
      val refTypePackageName = if (lastDot < 0) "" else name.substring(0, lastDot)
      packageName.isEmpty || packageName.contains(refTypePackageName)
    }

    def isAppropriate(refType: ReferenceType) = {
      Try(samePackage(refType) && refType.isInitialized && condition(refType)).getOrElse(false)
    }

    for {
      refType <- debugProcess.getVirtualMachineProxy.allClasses.asScala
      if isAppropriate(refType)
    } yield {
      refType
    }
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
        case _: PsiWhiteSpace if document.getLineNumber(element.getTextRange.getEndOffset) == position.getLine =>
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
        case c: ControlFlowException => throw c
        case _: Throwable => firstElement
      }
    }
  }

  private def calcPosition(file: PsiFile, location: Location, lineNumber: Int): Option[SourcePosition] = {
    throwIfNotScalaFile(file)

    def isDefaultArgument(method: Method) = {
      val methodName = method.name()
      val lastDollar = methodName.lastIndexOf("$")
      if (lastDollar >= 0) {
        val (start, index) = methodName.splitAt(lastDollar + 1)
        (start.endsWith("$default$"), index)
      }
      else (false, "")
    }

    def findDefaultArg(possiblePositions: Seq[PsiElement], defaultArgIndex: String) : Option[PsiElement] = {
      try {
        val paramNumber = defaultArgIndex.toInt - 1
        possiblePositions.find {
          e =>
            val scParameters = PsiTreeUtil.getParentOfType(e, classOf[ScParameters])
            if (scParameters != null) {
              val param = scParameters.params(paramNumber)
              param.isDefaultParam && param.isAncestorOf(e)
            }
            else false
        }
      } catch {
        case c: ControlFlowException => throw c
        case _: Exception => None
      }
    }

    def calcElement(): Option[PsiElement] = {
      val possiblePositions = positionsOnLine(file, lineNumber)
      val currentMethod = location.method()

      lazy val (isDefaultArg, defaultArgIndex) = isDefaultArgument(currentMethod)

      def findPsiElementForIndyLambda(): Option[PsiElement] = {
        val lambdas = lambdasOnLine(file, lineNumber)
        val methods = indyLambdaMethodsOnLine(location.declaringType(), lineNumber)
        val methodsToLambdas = methods.zip(lambdas).toMap
        methodsToLambdas.get(currentMethod)
      }

      def functionExprBody(element: PsiElement): PsiElement = element match {
        case ScFunctionExpr(_, Some(body)) => body
        case _ => element
      }

      if (possiblePositions.size <= 1) {
        possiblePositions.headOption
      }
      else if (isIndyLambda(currentMethod)) {
        findPsiElementForIndyLambda().map(functionExprBody)
      }
      else if (isDefaultArg) {
        findDefaultArg(possiblePositions, defaultArgIndex)
      }
      else if (!isAnonfun(currentMethod)) {
        possiblePositions.find {
          case e: PsiElement if isLambda(e) => false
          case (_: ScExpression) childOf (_: ScParameter) => false
          case _ => true
        }
      }
      else {
        val generatingPsiElem = findElementByReferenceType(location.declaringType())
        possiblePositions
          .find(p => generatingPsiElem.contains(findGeneratingClassOrMethodParent(p)))
          .map(functionExprBody)
      }
    }

    calcElement().filter(_.isValid).map(SourcePosition.createFromElement)
  }

  @Nullable
  private def getPsiFileByReferenceType(refType: ReferenceType): PsiFile = {
    if (refType == null) return null
    if (refTypeToFileCache.contains(refType)) return refTypeToFileCache(refType)

    @RequiresReadLock
    def findFile() = {
      def withDollarTestName(originalQName: String): Option[String] = {
        ProgressManager.checkCanceled()
        val dollarTestSuffix = "$Test" //See SCL-9340
        if (originalQName.endsWith(dollarTestSuffix)) Some(originalQName)
        else if (originalQName.contains(dollarTestSuffix + "$")) {
          val index = originalQName.indexOf(dollarTestSuffix) + dollarTestSuffix.length
          Some(originalQName.take(index))
        }
        else None
      }
      def topLevelClassName(originalQName: String): String = {
        ProgressManager.checkCanceled()
        if (originalQName.endsWith(PackageObjectSingletonClassPackageSuffix)) originalQName
        else originalQName.replace(PackageObjectSingletonClassPackageSuffix, ".").takeWhile(_ != '$')
      }
      def tryToFindClass(name: String): Option[PsiClass] = {
        ProgressManager.checkCanceled()
        val classes = findClassesByQName(name, debugProcessScope, fallbackToProjectScope = true)
        classes.find(!_.is[ScObject]).orElse(classes.headOption)
      }

      val originalQName = NameTransformer.decode(nonLambdaName(refType))

      if (originalQName.endsWith(TopLevelDefinitionsSingletonClassNameSuffix))
        findFileWithTopLevelMembers(debugProcess.getProject, debugProcessScope.scope, originalQName).orNull
      else {
        val clazz = withDollarTestName(originalQName)
          .flatMap(tryToFindClass)
          .orElse(tryToFindClass(topLevelClassName(originalQName)))

        clazz.map(_.getNavigationElement.getContainingFile).orNull
      }
    }

    val file = ReadAction.nonBlocking(() => findFile())
      .expireWhen(() => debugProcess.getProject.isDisposed)
      .executeSynchronously()

    if (file != null && refType.methods().asScala.exists(isIndyLambda)) {
      isCompiledWithIndyLambdasCache.put(file, true)
    }
    refTypeToFileCache.put(refType, file)
    file
  }

  private def nameMatches(elem: PsiElement, refType: ReferenceType): Boolean = {
    val pattern = NamePattern.forElement(elem)
    pattern != null && pattern.matches(refType)
  }

  private def checkForIndyLambdas(refType: ReferenceType) = {
    if (!refTypeToFileCache.contains(refType)) {
      getPsiFileByReferenceType(refType)
    }
  }

  def findElementByReferenceType(refType: ReferenceType): Option[PsiElement] = {
    refTypeToElementCache.get(refType) match {
      case Some(Some(p)) if p.getElement != null => Some(p.getElement)
      case Some(Some(_)) | None =>
        val found = findElementByReferenceTypeInner(refType)
        refTypeToElementCache.update(refType, found.map { element =>
          element.createSmartPointer
        })
        found
      case Some(None) => None
    }
  }

  private def findElementByReferenceTypeInner(refType: ReferenceType): Option[PsiElement] = {

    val byName = findPsiClassByQName(refType, debugProcessScope) orElse findByShortName(refType)
    if (byName.isDefined) return byName

    val project = debugProcess.getProject

    val allLocations = Try(refType.allLineLocations().asScala).getOrElse(Seq.empty)

    val refTypeLineNumbers = allLocations.map(checkedLineNumber).filter(_ > 0)
    if (refTypeLineNumbers.isEmpty) return None

    val firstRefTypeLine = refTypeLineNumbers.min
    val lastRefTypeLine = refTypeLineNumbers.max
    val refTypeLines = firstRefTypeLine to lastRefTypeLine

    val file = getPsiFileByReferenceType(refType)
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
      checkLines(elem, document) && isGenerateClass(elem) && nameMatches(elem, refType)
    }

    def findCandidates(): Seq[PsiElement] = {
      def findAt(offset: Int): Option[PsiElement] = {
        val startElem = file.findElementAt(offset)
        startElem.parentsInFile.find(isAppropriateCandidate)
      }
      if (lastRefTypeLine - firstRefTypeLine >= 2 && firstRefTypeLine + 1 <= document.getLineCount - 1) {
        val offsetsInTheMiddle = Seq(
          document.getLineEndOffset(firstRefTypeLine),
          document.getLineEndOffset(firstRefTypeLine + 1)
        )
        offsetsInTheMiddle.flatMap(findAt).distinct
      }
      else {
        val firstLinePositions = positionsOnLine(file, firstRefTypeLine)
        val allPositions =
          if (firstRefTypeLine == lastRefTypeLine) firstLinePositions
          else firstLinePositions ++ positionsOnLine(file, lastRefTypeLine)
        allPositions.distinct.filter(isAppropriateCandidate)
      }
    }

    def filterWithSignature(candidates: Seq[PsiElement]): Seq[PsiElement] = {
      val applySignature = refType.methodsByName("apply").asScala.find(m => !m.isSynthetic).map(_.signature())
      if (applySignature.isEmpty) candidates
      else {
        candidates.filter(l => applySignature == lambdaJVMSignature(l))
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

  private def findByShortName(refType: ReferenceType): Option[PsiClass] = {
    val project = debugProcess.getProject

    if (DumbService.getInstance(project).isDumb) return None

    lazy val sourceName = cachedSourceName(refType).getOrElse("")

    def sameFileName(elem: PsiElement) = {
      val containingFile = elem.getContainingFile
      containingFile != null && containingFile.name == sourceName
    }

    val originalQName = NameTransformer.decode(refType.name)
    val withoutSuffix =
      if (originalQName.endsWith(PackageObjectSingletonClassPackageSuffix))
        originalQName //Q: isn't it a bug? Do we indeed not remove the suffix here?
      else {
        //This line handles multiple possible cases
        //For example in scala 2.11 trait with a concrete method, defined in package object will have
        // a "trait implementation class" with fqn `org.example.package$MyTrait$class`
        originalQName
          .replace(PackageObjectSingletonClassPackageSuffix, ".")
          .stripSuffix("$")
          .stripSuffix(TraitImplementationClassSuffix_211)
      }
    val lastDollar = withoutSuffix.lastIndexOf('$')
    val lastDot = withoutSuffix.lastIndexOf('.')
    val index = Seq(lastDollar, lastDot, 0).max + 1
    val name = withoutSuffix.drop(index)
    val isScalaObject = originalQName.endsWith("$")

    val cacheManager = ScalaShortNamesCacheManager.getInstance(project)
    val classes = cacheManager.getClassesByName(name, GlobalSearchScope.allScope(project)).toSeq

    val inSameFile = classes.filter(c => c.isValid && sameFileName(c))

    if (inSameFile.length == 1) classes.headOption
    else if (inSameFile.length >= 2) {
      if (isScalaObject) inSameFile.find(_.isInstanceOf[ScObject])
      else inSameFile.find(!_.isInstanceOf[ScObject])
    }
    else None
  }

  private def findContainingClass(refType: ReferenceType): Option[PsiElement] = {
    def classesByName(s: String) = {
      val vm = debugProcess.getVirtualMachineProxy
      vm.classesByName(s)
    }

    val fullName = refType.name()
    val containingClassName = DebuggerUtilsEx.getLambdaBaseClassName(fullName) match {
      case baseClassName: String => Some(baseClassName)
      case null =>
        val decoded = NameTransformer.decode(fullName)
        val index = decoded.lastIndexOf("$$")

        if (index < 0) None
        else Some(NameTransformer.encode(decoded.substring(0, index)))
    }

    for {
      name  <- containingClassName
      clazz <- classesByName(name).asScala.headOption
      elem  <- findElementByReferenceType(clazz)
    }
      yield elem
  }

  private def nonLambdaName(refType: ReferenceType): String = {
    val fullName = refType.name()
    //typeName can be SomeClass$$Lambda$1.1836643189
    DebuggerUtilsEx.getLambdaBaseClassName(fullName) match {
      case null => fullName
      case name => name
    }
  }

  /**
   * Retrieve potentially nested classes currently loaded to VM just by iterating all classes and taking into account
   * the name mangling - instead of using VirtualMachineProxy's nestedTypes method (with caches etc.).
   */
  private def getNestedClasses(outerClasses: collection.Seq[ReferenceType]) = {
    for {
      outer <- outerClasses
      nested <- debugProcess.getVirtualMachineProxy.allClasses().asScala
      if outer != nested && extractOuterTypeName(nested.name) == outer.name
    } yield nested
  }

  private def extractOuterTypeName(typeName: String) = typeName match {
    case outerAndNestedTypePartsPattern(outerTypeName, _) => outerTypeName
  }
}

object ScalaPositionManager {
  private val SCRIPT_HOLDER_CLASS_NAME: String = "Main$$anon$1"
  private val delayedInitBody = "delayedInit$body"

  private val instances = mutable.HashMap[DebugProcess, ScalaPositionManager]()
  private def cacheInstance(scPosManager: ScalaPositionManager): Unit = {
    val debugProcess = scPosManager.debugProcess

    instances.put(debugProcess, scPosManager)
    debugProcess.addDebugProcessListener(new DebugProcessListener {
      override def processDetached(process: DebugProcess, closedByUser: Boolean): Unit = {
        ScalaPositionManager.instances.remove(process)
        debugProcess.removeDebugProcessListener(this)
      }
    })
  }

  def instance(vm: VirtualMachine): Option[ScalaPositionManager] = instances.collectFirst {
    case (process, manager) if getVM(process).contains(vm) => manager
  }

  def instance(debugProcess: DebugProcess): Option[ScalaPositionManager] = instances.get(debugProcess)

  def instance(mirror: Mirror): Option[ScalaPositionManager] = instance(mirror.virtualMachine())

  private def getVM(debugProcess: DebugProcess) = {
    if (!DebuggerManagerThreadImpl.isManagerThread) None
    else {
      debugProcess.getVirtualMachineProxy match {
        case impl: VirtualMachineProxyImpl => Option(impl.getVirtualMachine)
        case _ => None
      }
    }
  }

  def positionsOnLine(file: PsiFile, lineNumber: Int): Seq[PsiElement] = {
    if (lineNumber < 0) return Seq.empty

    val scFile: ScalaFile = file match {
      case sf: ScalaFile => sf
      case _ => return Seq.empty
    }

    //stored in `file`, invalidated on `file` change
    val map: ConcurrentIntObjectMap[Seq[PsiElement]] = cachedInUserData("positionsOnLine.map", file, file) {
      ConcurrentCollectionFactory.createConcurrentIntObjectMap()
    }

    Option(map.get(lineNumber))
      .getOrElse(map.cacheOrGet(lineNumber, positionsOnLineInner(scFile, lineNumber)))
  }

  def checkedLineNumber(location: Location): Int =
    try location.lineNumber() - 1
    catch {case _: InternalError => -1}

  def cachedSourceName(refType: ReferenceType): Option[String] = {
    ScalaPositionManager.instance(refType).map(_.caches).flatMap(_.cachedSourceName(refType))
  }

  private def positionsOnLineInner(file: ScalaFile, lineNumber: Int): Seq[PsiElement] = {
    @RequiresReadLock
    def compute(): Seq[PsiElement] = {
      val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
      if (document == null || lineNumber >= document.getLineCount) return Seq.empty
      val startLine = document.getLineStartOffset(lineNumber)
      val endLine = document.getLineEndOffset(lineNumber)

      @RequiresReadLock
      def elementsOnTheLine(file: ScalaFile): Seq[PsiElement] = {
        val builder = ArraySeq.newBuilder[PsiElement]
        var elem = file.findElementAt(startLine)

        while (elem != null && elem.getTextOffset <= endLine) {
          ProgressManager.checkCanceled()
          elem match {
            case ChildOf(_: ScUnitExpr) | ChildOf(ScBlock()) =>
              builder += elem
            case ElementType(t) if ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(t) ||
              ScalaTokenTypes.ANY_BRACKETS_TOKEN_SET.contains(t) =>
            case _ =>
              builder += elem
          }
          elem = PsiTreeUtil.nextLeaf(elem, true)
        }
        builder.result()
      }

      @RequiresReadLock
      def findParent(element: PsiElement): Option[PsiElement] = {
        ProgressManager.checkCanceled()
        val parentsOnTheLine = element.withParentsInFile.takeWhile(e => e.getTextOffset > startLine).toIndexedSeq
        val anon = parentsOnTheLine.collectFirst {
          case e if isLambda(e) => e
          case newTd: ScNewTemplateDefinition if generatesAnonClass(newTd) => newTd
        }
        val filteredParents = parentsOnTheLine.reverse.filter {
          case _: ScExpression => true
          case _: ScConstructorPattern | _: ScInfixPattern | _: ScBindingPattern => true
          case callRefId childOf ((ref: ScReferenceExpression) childOf (_: ScMethodCall))
            if ref.nameId == callRefId && ref.getTextRange.getStartOffset < startLine => true
          case _: ScTypeDefinition => true
          case _ => false
        }
        val maxExpressionPatternOrTypeDef =
          filteredParents.find(!_.is[ScBlock]).orElse(filteredParents.headOption)
        Seq(anon, maxExpressionPatternOrTypeDef).flatten.sortBy(_.getTextLength).headOption
      }

      elementsOnTheLine(file).flatMap(findParent).distinct
    }

    ReadAction.nonBlocking(() => compute())
      .expireWhen(() => !file.isValid)
      .executeSynchronously()
  }

  def isLambda(element: PsiElement): Boolean = {
    isGenerateAnonfun211(element) && !isInsideMacro(element)
  }

  def lambdasOnLine(file: PsiFile, lineNumber: Int): Seq[PsiElement] = {
    positionsOnLine(file, lineNumber).filter(isLambda)
  }

  def isIndyLambda(m: Method): Boolean = {
    val name = m.name()
    name.contains("$anonfun$") && !name.contains("$anonfun$adapted$") && name.charAt(name.length - 1).isDigit
  }

  def isAnonfunType(refType: ReferenceType): Boolean = {
    refType match {
      case ct: ClassType =>
        val supClass = ct.superclass()
        supClass != null && supClass.name().startsWith("scala.runtime.AbstractFunction")
      case _ => false
    }
  }

  def isAnonfun(m: Method): Boolean = {
    isIndyLambda(m) || m.name.startsWith("apply") && isAnonfunType(m.declaringType())
  }

  def indyLambdaMethodsOnLine(refType: ReferenceType, lineNumber: Int): Seq[Method] = {
    def ordinal(m: Method) = {
      val name = m.name()
      val lastDollar = name.lastIndexOf('$')
      Try(name.substring(lastDollar + 1).toInt).getOrElse(-1)
    }

    val all = refType.methods().asScala.iterator.filter(isIndyLambda)
    val onLine = all.filter(m => Try(!m.locationsOfLine(lineNumber + 1).isEmpty).getOrElse(false))
    onLine.toSeq.sortBy(ordinal)
  }

  @tailrec
  def findGeneratingClassOrMethodParent(element: PsiElement): PsiElement = {
    element match {
      case null => null
      case f: ScalaFile   => f
      case p: ScPackaging => p
      case elem if isGenerateClass(elem) || isLambda(elem) => elem
      case elem if isMacroCall(elem) => elem
      case elem => findGeneratingClassOrMethodParent(elem.getParent)
    }
  }

  def isInsideMacro(elem: PsiElement): Boolean = elem.parentsInFile.exists(isMacroCall)

  def shouldSkip(location: Location, debugProcess: DebugProcess): Boolean = {
    ScalaPositionManager.instance(debugProcess).forall(_.shouldSkip(location))
  }

  private def getSpecificNameForDebugger(td: ScTypeDefinition): String = {
    val name = td.getQualifiedNameForDebugger

    td match {
      case _: ScObject => name + "$"
      case _: ScTrait => name + TraitImplementationClassSuffix_211 //is valid only before scala 2.12
      case _ => name
    }
  }

  def isDelayedInit(cl: PsiClass): Boolean = cl match {
    case obj: ScObject =>
      val manager: ScalaPsiManager = ScalaPsiManager.instance(obj.getProject)
      val clazz: PsiClass =
        manager.getCachedClass(obj.resolveScope, "scala.DelayedInit").orNull
      clazz != null && obj.isInheritor(clazz, true)
    case _ => false
  }

  private def isGeneratedClass(generatedClassName: String, refType: ReferenceType): Boolean = {
    if (generatedClassName == null)
      return false

    val name = refType.name()
    val index = name.lastIndexOf(generatedClassName)

    index >= 0 && {
      val suffix = name.substring(index + generatedClassName.length)
      //we need exact class, not possible lambdas inside
      //but local classes may have suffices like $1
      !suffix.exists(_.isLetter)
    }
  }

  private[debugger] def findPackageName(position: PsiElement): Option[String] = {
    def packageWithName(e: PsiElement): Option[String] = e match {
      case p: ScPackaging => Some(p.fullPackageName)
      case obj: ScObject if obj.isPackageObject => Some(obj.qualifiedName.stripSuffix(PackageObjectSingletonClassName))
      case _ => None
    }

    position.parentsInFile.flatMap(packageWithName).nextOption()
  }

  private class MyClassPrepareRequestor(position: SourcePosition, requestor: ClassPrepareRequestor) extends ClassPrepareRequestor {
    private val sourceFile = position.getFile
    private val sourceName = sourceFile.getName
    private def sourceNameOf(refType: ReferenceType): Option[String] = ScalaPositionManager.cachedSourceName(refType)

    override def processClassPrepare(debuggerProcess: DebugProcess, referenceType: ReferenceType): Unit = {
      val positionManager = debuggerProcess.getPositionManager

      if (!sourceNameOf(referenceType).contains(sourceName)) return

      if (positionManager.locationsOfLine(referenceType, position).size > 0) {
        requestor.processClassPrepare(debuggerProcess, referenceType)
      }
      else {
        val positionClasses: ju.List[ReferenceType] = positionManager.getAllClasses(position)
        if (positionClasses.contains(referenceType)) {
          requestor.processClassPrepare(debuggerProcess, referenceType)
        }
      }
    }
  }

  private class NamePattern(elem: PsiElement) {
    private val containingFile = elem.getContainingFile
    private val sourceName = containingFile.getName
    private val isGeneratedForCompilingEvaluator = containingFile.getUserData(ScalaCompilingEvaluator.classNameKey) != null
    private var compiledWithIndyLambdas = isCompiledWithIndyLambdas(containingFile)
    private val exactName: Option[String] = {
      elem match {
        case td: ScTypeDefinition if !isLocalClass(td) =>
          Some(getSpecificNameForDebugger(td))
        case _ => None
      }
    }
    private var classJVMNameParts: Seq[String] = _

    private def computeClassJVMNameParts(elem: PsiElement): Seq[String] = {
      if (exactName.isDefined) Seq.empty
      else {
        ReadAction.nonBlocking(() => {
          elem match {
            case InsideMacro(call) => computeClassJVMNameParts(call.getParent)
            case _ =>
              val parts = elem.withParentsInFile.flatMap(partsFor)
              parts.toSeq.reverse
          }
        }).expireWhen(() => !elem.isValid).executeSynchronously()
      }
    }

    @RequiresReadLock
    private def partsFor(elem: PsiElement): Seq[String] = {
      ProgressManager.checkCanceled()
      elem match {
        case o: ScObject if o.isPackageObject => Seq(PackageObjectSingletonClassName)
        case td: ScTypeDefinition => Seq(ScalaNamesUtil.toJavaName(td.name))
        case newTd: ScNewTemplateDefinition if generatesAnonClass(newTd) => Seq("$anon")
        case e if isGenerateClass(e) => partsForAnonfun(e)
        case _ => Seq.empty
      }
    }

    @RequiresReadLock
    private def partsForAnonfun(elem: PsiElement): Seq[String] = {
      ProgressManager.checkCanceled()
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
      updateParts()

      for (part <- classJVMNameParts) {
        val index = nameTail.indexOf(part)
        if (index >= 0) {
          nameTail = nameTail.substring(index + part.length)
        }
        else return false
      }
      nameTail.indexOf("$anon") == -1
    }

    def updateParts(): Unit = {
      val newValue = isCompiledWithIndyLambdas(containingFile)
      if (newValue != compiledWithIndyLambdas || classJVMNameParts == null) {
        compiledWithIndyLambdas = newValue
        classJVMNameParts = computeClassJVMNameParts(elem)
      }
    }

    def matches(refType: ReferenceType): Boolean = {
      val refTypeSourceName = cachedSourceName(refType).getOrElse("")
      if (refTypeSourceName != sourceName && !isGeneratedForCompilingEvaluator) return false

      val name = refType.name()

      exactName match {
        case Some(qName) => qName == name || qName.stripSuffix(TraitImplementationClassSuffix_211) == name
        case None => checkParts(name)
      }
    }
  }

  private object NamePattern {
    def forElement(elem: PsiElement): NamePattern = {
      if (elem == null || !isGenerateClass(elem)) return null

      val cacheProvider = new CachedValueProvider[NamePattern] {
        override def compute(): Result[NamePattern] = Result.create(new NamePattern(elem), elem)
      }

      CachedValuesManager.getCachedValue(elem, cacheProvider)
    }
  }

  private[debugger] class ScalaPositionManagerCaches(debugProcess: DebugProcess) {

    debugProcess.addDebugProcessListener(new DebugProcessListener {
      override def processDetached(process: DebugProcess, closedByUser: Boolean): Unit = {
        clear()
        process.removeDebugProcessListener(this)
      }
    })

    val refTypeToFileCache: mutable.HashMap[ReferenceType, PsiFile] =
      mutable.HashMap[ReferenceType, PsiFile]()
    val refTypeToElementCache: mutable.HashMap[ReferenceType, Option[SmartPsiElementPointer[PsiElement]]] =
      mutable.HashMap[ReferenceType, Option[SmartPsiElementPointer[PsiElement]]]()

    val customizedLocationsCache: mutable.HashMap[Location, Int] = mutable.HashMap[Location, Int]()
    val lineToCustomizedLocationCache: mutable.HashMap[(ReferenceType, Int), Seq[Location]] = mutable.HashMap[(ReferenceType, Int), Seq[Location]]()
    val seenRefTypes: mutable.Set[ReferenceType] = mutable.Set[ReferenceType]()
    val sourceNames: mutable.HashMap[ReferenceType, Option[String]] = mutable.HashMap[ReferenceType, Option[String]]()

    def cachedSourceName(refType: ReferenceType): Option[String] =
      sourceNames.getOrElseUpdate(refType, Try(refType.sourceName()).toOption)

    def clear(): Unit = {
      isCompiledWithIndyLambdasCache.clear()

      refTypeToFileCache.clear()
      refTypeToElementCache.clear()

      customizedLocationsCache.clear()
      lineToCustomizedLocationCache.clear()
      seenRefTypes.clear()
      sourceNames.clear()
    }
  }

  private sealed trait ClassPattern
  private object ClassPattern {
    final case class Single(pattern: String) extends ClassPattern
    final case class Nested(pattern: String) extends ClassPattern
  }
}
