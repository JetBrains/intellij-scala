package org.jetbrains.plugins.scala.lang.parser.util;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.DummyHolderElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * HIGHLY experimental
 * 
 * @author Dmitry Naydanov
 */
public abstract class LayeredParser implements PsiParser {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.parser.util.LayeredParser");
  private static final ASTNode fakeTreeBuilt = new DummyHolderElement("ololo");

  private final List<RegisteredInfo<?, ?>> myParsers = new ArrayList<RegisteredInfo<?, ?>>();
  private final List<IElementType> mySubRootElements = new ArrayList<IElementType>();
  
  private TokenSet myEofExtendedElements = TokenSet.EMPTY;
  private boolean isDebug = false;
  private ConflictResolver myResolver = new DefaultConflictResolver();


  @NotNull
  @Override
  public ASTNode parse(IElementType root, @NotNull PsiBuilder builder) { //WARNING: DON'T ADD NotNull ANNOTATION TO 'root' PARAMETER
    LayeredParserPsiBuilder delegateBuilder = new LayeredParserPsiBuilder(builder);
    if (isDebug) delegateBuilder.setDebugMode(true);
    
    for (RegisteredInfo<?, ?> parserInfo : myParsers) {
      if (delegateBuilder.initParser(parserInfo)) {
        parserInfo.parse(delegateBuilder);
        
        while (delegateBuilder.isReparseNeeded()) {
          delegateBuilder.subInit();
          parserInfo.parse(delegateBuilder);
        }
      }
    }
    
    delegateBuilder.copyMarkersWithRoot(root);
    return delegateBuilder.getDelegateTreeBuilt();  
  }

  protected void register(RegisteredInfo<?, ?> info) {
    myParsers.add(info);
  }
  
  protected void setSubRootElements(Collection<IElementType> elements) {
    mySubRootElements.addAll(elements);
  }
  
//  protected void clearSubRootElements() {
//    mySubRootElements.clear();
//  }
  
  protected void setEofExtendedElements(IElementType... elements) {
    myEofExtendedElements = TokenSet.create(elements);
  }
  
  protected void setDebug(boolean isDebug) {
    this.isDebug = isDebug;
  }
  
  protected void setConflictResolver(ConflictResolver resolver) {
    myResolver = resolver;
  }
  
  protected void logError(String message) {
    LOG.error("[Scala Layered Parser] " + message);
//    System.out.println("[Scala Layered Parser] " + message);
  }


  private class LayeredParserPsiBuilder extends PsiBuilderAdapter {
    private final List<BufferedTokenInfo> originalTokens;
    private final BufferedTokenInfo fakeEndToken;
//    private final TreeMap<Integer, Integer> validNumbersLookUp;
    private final BitSet usedTokens = new BitSet();
    private final IElementType defaultWhitespaceToken;

    private ITokenTypeRemapper myRemapper;
    private RegisteredInfo<?, ?> myCurrentParser;

    private StateFlusher currentStateFlusher;
    private List<Integer> filteredTokens;

    private TokenSet commentTokens = TokenSet.EMPTY;
    private WhitespaceSkippedCallback myWhitespaceSkippedCallback;
    private boolean isDebugMode;

    private LinkedList<Integer> stateFlushedNums;
    private int currentTokenNumber;
    private FakeMarker lastMarker;

    private BufferedTokenInfo backStepToken;
    private int backStepNumber;


    LayeredParserPsiBuilder(PsiBuilder delegate) {
      this(delegate, TokenType.WHITE_SPACE);
    }

    LayeredParserPsiBuilder(PsiBuilder delegate, IElementType defaultWhitespaceToken) {
      super(delegate);
      this.defaultWhitespaceToken = defaultWhitespaceToken;
      fakeEndToken = new BufferedTokenInfo(null, false, delegate.getOriginalText().length(), delegate.getOriginalText().length());

      //carefully copypasted from PsiBuilderImpl 
      final int approxLength = Math.max(10, delegate.getOriginalText().length() / 5);
      final Ref<Integer> validTokensCountRef = new Ref<Integer>();
      validTokensCountRef.set(0);

      delegate.setWhitespaceSkippedCallback(new WhitespaceSkippedCallback() {
        @Override
        public void onSkip(IElementType type, int start, int end) {
//          int count = validTokensCountRef.get() + 1;
//          validNumbersLookUp.put(originalTokens.size(), count);
//          validTokensCountRef.set(count);
          originalTokens.add(new BufferedTokenInfo(type, true, start, end));
        }
      });

      originalTokens = new ArrayList<BufferedTokenInfo>(approxLength);
//      validNumbersLookUp = new TreeMap<Integer, Integer>();

      Marker rollbackMarker = delegate.mark();
      while (!delegate.eof()) {
        originalTokens.add(
                new BufferedTokenInfo(delegate.getTokenType(), false, delegate.getCurrentOffset(), -1)
        );
        delegate.advanceLexer();
      }
      rollbackMarker.rollbackTo();
    }

    /**
     * Inits current PsiParser
     * @return true if parser should be ran 
     */
    private boolean initParser(RegisteredInfo<?, ?> currentParser) {
      backStepToken = null;
      myCurrentParser = currentParser;
      filteredTokens = new ArrayList<Integer>();
      stateFlushedNums = new LinkedList<Integer>();
      stateFlushedNums.add(0);

      List<Class<? extends IElementType>> currentTokenTypes = currentParser.getMyRegisteredTokens();
      currentStateFlusher = currentParser.getMyStateFlusher();

      ListIterator<BufferedTokenInfo> it = originalTokens.listIterator();
      while (it.hasNext()) {
        final BufferedTokenInfo nextInfo = it.next();
        final int index = it.nextIndex() - 1;
        final IElementType tokenType = nextInfo.getTokenType();

        for (Class<? extends IElementType> elementTypeClass : currentTokenTypes) {
          currentParser.processNextTokenWithChooser(tokenType);

          if ((elementTypeClass.isInstance(tokenType) && !currentParser.mustRejectOwnToken(tokenType) ||
                  currentParser.mustTakeForeignToken(tokenType)) && !usedTokens.get(index)) {
            if (filteredTokens.isEmpty() && nextInfo.isWhitespace()) continue;
            filteredTokens.add(index);
            usedTokens.set(index);

            if (currentStateFlusher.isFlushOnBuilderNeeded(tokenType)) {
              stateFlushedNums.add(index);
            }
          }
        }
      }
      if (filteredTokens.isEmpty()) return false;
      subInit();

      return true;
    }

    @Override
    public void setTokenTypeRemapper(ITokenTypeRemapper remapper) {
      myRemapper = remapper;
    }

    @Override
    public boolean eof() {
      if (stateFlushedNums != null && !stateFlushedNums.isEmpty()) {
        return stateFlushedNums.peekFirst() <= currentTokenNumber;
      }
      return filteredTokens == null || currentTokenNumber >= filteredTokens.size();
    }

    @Override
    public void setDebugMode(boolean dbgMode) {
      super.setDebugMode(dbgMode);
      this.isDebugMode = dbgMode;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
      if (eof()) return null;

      int num = filteredTokens.get(currentTokenNumber);
      BufferedTokenInfo tokenInfo = originalTokens.get(num).isWhitespace() ?
              getValidTokenInfo(currentTokenNumber) : originalTokens.get(num);

      if (myRemapper != null) {
        tokenInfo.setTokenType(myRemapper.filter(tokenInfo.getTokenType(), myDelegate.rawTokenTypeStart(num),
                myDelegate.rawTokenTypeStart(num), myDelegate.getOriginalText()));
      }
      return tokenInfo.getTokenType();
    }

    @Override
    public void advanceLexer() {
      if (eof()) return;

      BufferedTokenInfo currentValidInfo = getCurrentTokenInfo().isWhitespace() ? getValidTokenInfo(currentTokenNumber)
              : getCurrentTokenInfo(); //because of VERY STRANGE behaviour of PsiBuilderImpl
      backStepToken = null;
      subAdvanceLexer();

      BufferedTokenInfo currentToken = getCurrentTokenInfo();

      while (!eof() && (commentTokens.contains(currentToken.getTokenType()) || currentToken.isWhitespace()
              || currentToken == currentValidInfo)) {
        if (myWhitespaceSkippedCallback != null) {
          myWhitespaceSkippedCallback.onSkip(currentToken.getTokenType(), currentToken.getTokenStart(), currentToken.getTokenEnd());
        }
        subAdvanceLexer();
        currentToken = getCurrentTokenInfo();
      }
    }

    @Override
    public void remapCurrentToken(IElementType type) {
      if (eof()) return;
      getCurrentTokenInfo().setTokenType(type);//todo getValidTokenInfo?
    }

    @Override
    public void setWhitespaceSkippedCallback(WhitespaceSkippedCallback callback) {
      myWhitespaceSkippedCallback = callback;
    }

    @Override
    public IElementType lookAhead(int steps) {
      final int index = getIndexWithStateFlusher(steps + currentTokenNumber);
      return eof() || index >= filteredTokens.size() ? null : getValidTokenInfo(index).getTokenType();
    }

    @Override
    public int rawTokenTypeStart(int steps) {
      int lookup = getIndexWithStateFlusher(steps + currentTokenNumber);

      if (eof() || lookup < 0) return 0;
      return getTokenInfoByRelativeNumber(lookup).getTokenStart();
    }

    @Override
    public IElementType rawLookup(int steps) {
      int lookup = getIndexWithStateFlusher(steps + currentTokenNumber);
      if (eof() || lookup < 0 || lookup >= filteredTokens.size() - 1) return null;

      return originalTokens.get(filteredTokens.get(lookup)).getTokenType(); //todo furhter bugs in the parser can be caused by this fix
    }

    @NotNull
    @Override
    public ASTNode getTreeBuilt() {
      return fakeTreeBuilt;
    }

    @NotNull
    @Override
    public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
      throw new UnsupportedOperationException();//todo
    }

    @Override
    public void enforceCommentTokens(@NotNull TokenSet tokens) {
      commentTokens = tokens;
    }

    @Override
    public int getCurrentOffset() {
      if (eof()) return getOriginalText().length();
      return getCurrentTokenInfo().getTokenStart();//todo getValidTokenInfo?
    }

    @NotNull
    @Override
    public Marker mark() {
      FakeStartMarker marker = eof() ? new FakeStartMarker(myDelegate.getOriginalText().length(), originalTokens.size())
              : new FakeStartMarker(myDelegate.rawTokenTypeStart(filteredTokens.get(currentTokenNumber)), currentTokenNumber);
      BufferedTokenInfo info = getCurrentTokenInfo();

      (info.isWhitespace() ? getValidTokenInfo(currentTokenNumber) : info).addProductionMarker(marker);

      advanceMarker(marker);
      return marker;
    }

    @Override
    public void error(String messageText) {
      FakeErrorMarker errorMarker = new FakeErrorMarker(messageText);
      getCurrentTokenInfo().setMyErrorMarker(errorMarker);//todo getValidTokenInfo?
      advanceMarker(errorMarker);
    }

    @Nullable
    @Override
    public LighterASTNode getLatestDoneMarker() {
      FakeMarker marker = lastMarker;

      while (marker != null && !(marker instanceof FakeEndMarker)) {
        marker = marker.getPrevMarker();
      }
      return (FakeEndMarker) marker;
    }

    @Nullable
    @Override
    public String getTokenText() {
      if (eof()) return null;

      int tokenNumber = getCurrentTokenInfo().isWhitespace() ?
              getValidTokenNum(currentTokenNumber) : filteredTokens.get(currentTokenNumber);


      return getOriginalText().subSequence(myDelegate.rawTokenTypeStart(tokenNumber),
              myDelegate.rawTokenTypeStart(tokenNumber + 1)).toString();
    }

    boolean isReparseNeeded() {
      return stateFlushedNums != null && !stateFlushedNums.isEmpty() && currentTokenNumber < stateFlushedNums.getLast();
    }

    void subInit() {
      if (!stateFlushedNums.isEmpty()) currentTokenNumber = stateFlushedNums.pollFirst();
    }

    ASTNode getDelegateTreeBuilt() {
      return myDelegate.getTreeBuilt();
    }

    void copyMarkersWithRoot(IElementType rootElementType) {
      final PsiBuilder.Marker rootMarker = rootElementType != null ? myDelegate.mark() : null;
      final List<Pair<Marker, IElementType>> subRootMarkers = mySubRootElements.isEmpty() ?
              Collections.<Pair<Marker, IElementType>>emptyList() :
              new ArrayList<Pair<Marker, IElementType>>(mySubRootElements.size());
      for (IElementType tpe : mySubRootElements) {
        subRootMarkers.add(new Pair<Marker, IElementType>(myDelegate.mark(), tpe));
      }

      final Stack<FakeStartMarker> openMarkers = new Stack<FakeStartMarker>();
      myDelegate.setWhitespaceSkippedCallback(null);


      for (BufferedTokenInfo info : originalTokens) {
        if (info.isWhitespace()) {
          if (info.getTokenType() == myDelegate.getTokenType()) {
            myDelegate.advanceLexer(); //because of VERY STRANGE behaviour of PsiBuilderImpl
          }

          continue;
        }

        List<FakeMarker> productionMarkerList = info.getAllMarkers();
        FakeErrorMarker errorMarker = info.getMyErrorMarker();
        IElementType probablyRemappedElementType = info.getTokenType();

        if (myDelegate.getTokenType() != probablyRemappedElementType && probablyRemappedElementType != null) {
          myDelegate.remapCurrentToken(probablyRemappedElementType);
        }
        if (errorMarker != null) myDelegate.error(errorMarker.getMessage());

        if (productionMarkerList != null)
          for (FakeMarker productionMarker : productionMarkerList) {
            processFakeMarker(productionMarker, openMarkers);
          }

        myDelegate.advanceLexer();
      }

      while (!myDelegate.eof()) myDelegate.advanceLexer();

      //reference PsiBuilderImpl allows markers after eof, so we must emulate it 
      if (fakeEndToken.hasMarkers()) {
        if (fakeEndToken.getMyErrorMarker() != null) myDelegate.error(fakeEndToken.getMyErrorMarker().getMessage());
        List<FakeMarker> eofMarkers = fakeEndToken.getAllMarkers();

        if (eofMarkers != null) {
          int endIndex = 0;
          while (endIndex < eofMarkers.size() && eofMarkers.get(endIndex) instanceof FakeStartMarker) {
            ++endIndex;
          }

          if (endIndex > 0) {
            Collections.sort(eofMarkers.subList(0, endIndex), new Comparator<FakeMarker>() {
              @Override
              public int compare(@NotNull FakeMarker o1, @NotNull FakeMarker o2) {
                return o2.getStartOffset() - o1.getStartOffset();
              }
            });
          }

          for (FakeMarker marker : eofMarkers) processFakeMarker(marker, openMarkers);
        }
      }

      for (ListIterator<Pair<Marker, IElementType>> iterator = subRootMarkers.listIterator(subRootMarkers.size()); iterator.hasPrevious(); ) {
        Pair<Marker, IElementType> listElement = iterator.previous();
        listElement.getFirst().done(listElement.getSecond());
      }

      if (rootMarker != null) rootMarker.done(rootElementType);
    }

    private void processFakeMarker(FakeMarker fakeMarker, Stack<FakeStartMarker> openMarkers) {
      if (!fakeMarker.isValid()) return;

      if (fakeMarker instanceof FakeStartMarker) {
        final Marker marker = myDelegate.mark();
        final FakeStartMarker fakeStartMarker = (FakeStartMarker) fakeMarker;

        openMarkers.push(fakeStartMarker);
        fakeStartMarker.setDelegateMarker(marker);
        if (fakeStartMarker.getEndMarker() != null) fakeStartMarker.getEndMarker().setValid(true);
      } else if (fakeMarker instanceof FakeEndMarker) {
        final FakeEndMarker endMarker = (FakeEndMarker) fakeMarker;

        while (!openMarkers.isEmpty() && openMarkers.peek() != endMarker.getStartMarker()) {
          FakeStartMarker markerToClose = openMarkers.pop();
          FakeEndMarker endMarkerToClose = markerToClose.getEndMarker();

//          if (!(endMarkerToClose instanceof FakeEndWithErrorMarker)) {
//            logError("Overlapping markers: {" + markerToClose + "; " + markerToClose.getEndMarker() + "}  {" +
//                endMarker.getStartMarker() + "; " + endMarker + "}");
//          }

          endMarkerToClose.doneMarker();
          setMarkerCustomEdgeBinder(endMarkerToClose);
          endMarkerToClose.setValid(false);
        }

        if (openMarkers.isEmpty()) {
          logError("Attempt to .done not added marker: " + endMarker);
          endMarker.setValid(false);
          return;
        }

        openMarkers.pop();
        endMarker.doneMarker();
        setMarkerCustomEdgeBinder(endMarker);
      }
    }

    private void setMarkerCustomEdgeBinder(FakeEndMarker endMarker) {
      FakeStartMarker startMarker = endMarker.getStartMarker();

      Pair<WhitespacesAndCommentsBinder, WhitespacesAndCommentsBinder> pair = startMarker.getMyCustomEdgeTokenBinders();
      if (pair != null && startMarker.getDelegateMarker() != null) {
        startMarker.getDelegateMarker().setCustomEdgeTokenBinders(pair.getFirst(), pair.getSecond());
      }
    }

    private BufferedTokenInfo getTokenInfoByRelativeNumber(int index) {
      return index < filteredTokens.size() ? originalTokens.get(filteredTokens.get(index)) : fakeEndToken;
    }

    private BufferedTokenInfo getCurrentTokenInfo() {
      return getTokenInfoByRelativeNumber(currentTokenNumber);
    }

    private IElementType remapAstElement(IElementType oldType) {
      return myCurrentParser.getMyElementRemapper().remap(oldType);
    }

    private int getValidTokenNum(int filteredTokenNumber) {
      if (filteredTokenNumber >= filteredTokens.size()) return originalTokens.size();

      final int originalNumber = filteredTokens.get(filteredTokenNumber);
      return originalNumber > originalTokens.size() ? originalTokens.size() : originalNumber;
    }

    private BufferedTokenInfo getValidTokenInfo(int filteredTokenNumber) {
      final int rawNumber = getValidTokenNum(filteredTokenNumber);
      return rawNumber == originalTokens.size() ? fakeEndToken : originalTokens.get(rawNumber);
    }

    private int getIndexWithStateFlusher(int index) {
      return stateFlushedNums != null && !stateFlushedNums.isEmpty() ? Math.min(index, stateFlushedNums.peekFirst()) : index;
    }

    private FakeEndMarker createEndMarker(FakeStartMarker startMarker, @NotNull IElementType astElementType, int endTokenNum,
                                          @Nullable String errorMessage) {
      final int originalNum = endTokenNum < filteredTokens.size() ? filteredTokens.get(endTokenNum) : filteredTokens.get(filteredTokens.size() - 1);
      final int tokenEndOffset = originalNum >= originalTokens.size() - 2 ? myDelegate.getOriginalText().length()
              : getCurrentOffset();

      final FakeEndMarker endMarker = errorMessage == null ? new FakeEndMarker(startMarker, astElementType, tokenEndOffset)
              : new FakeEndWithErrorMarker(startMarker, astElementType, tokenEndOffset, errorMessage);
      startMarker.setEndMarker(endMarker);
      return endMarker;
    }

    private void advanceMarker(FakeMarker marker) {
      if (lastMarker == null) {
        lastMarker = marker;
        return;
      }
      lastMarker.setNextMarker(marker);
      marker.setPrevMarker(lastMarker);
      lastMarker = marker;
    }

    private boolean checkStartMarker(FakeStartMarker marker, String message) {
      if (getTokenInfoByRelativeNumber(marker.getMyTokenNum()).getAllMarkers().contains(marker) && marker.isValid()) {
        return true;
      }
      logError(message);
      return false;
    }

    private boolean checkEndMarker(FakeStartMarker markerToBeDone, IElementType astElementType) {
      if (astElementType == null || myCurrentParser.isIgnored(astElementType)) {
        markerToBeDone.drop();
        return false;
      }

      return true;
    }

    /**
     * Determines if endMarker with particular `.done` type must be "EOF-extended"
     *
     * @param startOffset   element start
     * @param astElementType  `.done(...)` IElementType
     * @return true if must be extended, false otherwise
     */
    private boolean checkEofExtendedElement(IElementType astElementType, int startOffset) {
      if (getCurrentTokenInfo() != fakeEndToken ||
              !myEofExtendedElements.contains(astElementType)) return false;

      int startPoint = backStepNumber;
      if (backStepToken == null) {
        for (int j = filteredTokens.size() - 1; j > 0; --j) {
          if (!getTokenInfoByRelativeNumber(j).isWhitespace()) {
            startPoint = filteredTokens.get(j);
            break;
          }
        }
      }

      for (int i = startPoint; i < originalTokens.size(); ++i) {
        if (!checkTokenForEof(originalTokens.get(i), startOffset)) return false;
      }

      return checkTokenForEof(fakeEndToken, startOffset);
    }

    private boolean checkTokenForEof(BufferedTokenInfo tokenInfo, int startOffset) {
      List<FakeMarker> list = tokenInfo.getAllMarkers();
      if (list != null) {
        for (FakeMarker marker : list) {
          if (marker.isValid() && marker instanceof FakeEndMarker && ((FakeEndMarker) marker).getStartMarker().getStartOffset() < startOffset)
            return false;
        }
      }

      return true;
    }

    private boolean checkBackStepMarker(IElementType astDoneElementType) {
      return backStepToken != null &&
              myResolver.needDoBackStep(backStepToken.getTokenType(), astDoneElementType, getTokenType(), getTokenText());
    }

    private void subAdvanceLexer() {
      int oldOriginalNumber = filteredTokens.get(currentTokenNumber) + 1;
      ++currentTokenNumber;

      if (currentTokenNumber >= filteredTokens.size()) return;
      int newOriginalNumber = filteredTokens.get(currentTokenNumber) - 1;

      for (int i = oldOriginalNumber; i <= newOriginalNumber; ++i) {
        BufferedTokenInfo tokenInfo = originalTokens.get(i);
        if (!tokenInfo.isWhitespace() && backStepToken == null) {
          backStepToken = tokenInfo;
          backStepNumber = i;
        }

        if (myWhitespaceSkippedCallback != null) {
          if (tokenInfo.isWhitespace()) {
            myWhitespaceSkippedCallback.onSkip(tokenInfo.getTokenType(), tokenInfo.getTokenStart(),
                    tokenInfo.getTokenEnd());
          } else {
            myWhitespaceSkippedCallback.onSkip(defaultWhitespaceToken, tokenInfo.getTokenStart(),
                    originalTokens.get(i + 1).getTokenStart());
          }
        }
      }
    }

    private FakeMarker precede(FakeStartMarker startMarker) {
      BufferedTokenInfo tokenInfo = getTokenInfoByRelativeNumber(startMarker.getMyTokenNum());
      FakeStartMarker newMarker = new FakeStartMarker(tokenInfo.getTokenStart(), startMarker.getMyTokenNum());
      tokenInfo.addProductionMarkerBefore(newMarker, startMarker);
      insertMarkerBefore(newMarker, startMarker);
      return newMarker;
    }

    private void rollbackTo(FakeStartMarker marker) {
      lastMarker = marker.getPrevMarker();

      for (int i = marker.getMyTokenNum(); i <= currentTokenNumber; ++i) {  //todo change errors to regular markers and delete this
        getTokenInfoByRelativeNumber(i).setMyErrorMarker(null);
      }
      currentTokenNumber = marker.getMyTokenNum();

      FakeMarker current = marker;
      while (current != null) {
        current.setValid(false);
        current = current.getNextMarker();
      }
    }

    private void done(FakeMarker marker, IElementType astElementType, @Nullable String errorMessage) {
      astElementType = remapAstElement(astElementType);

      final FakeStartMarker fakeStartMarker = (FakeStartMarker) marker;


      if (!checkEndMarker(fakeStartMarker, astElementType)) {
        return;
      }

      final FakeEndMarker endMarker = createEndMarker(fakeStartMarker, astElementType, currentTokenNumber, errorMessage);
      BufferedTokenInfo currentTokenInfo = getCurrentTokenInfo().isWhitespace() ?
              getValidTokenInfo(currentTokenNumber) : getCurrentTokenInfo();
      advanceMarker(endMarker);

      if (currentTokenInfo == fakeEndToken && checkEofExtendedElement(astElementType, fakeStartMarker.getStartOffset())) {
        fakeEndToken.addProductionMarker(endMarker);
        return;
      }

      if (backStepToken != null && fakeStartMarker.getStartOffset() < backStepToken.getTokenStart() /*&&  
          !(currentTokenInfo == fakeEndToken && myEofExtendedElements.contains(astElementType))*/ && checkBackStepMarker(astElementType)) {
        backStepToken.addForeignProductionMarker(endMarker);
        endMarker.setEndOffset(backStepToken.getTokenStart());
        return;
      }

      if (currentTokenInfo == fakeEndToken && filteredTokens.get(filteredTokens.size() - 1) < originalTokens.size() - 1 &&
              fakeStartMarker.getStartOffset() != fakeEndToken.getTokenStart()) {
        int lastNonWsIndex = filteredTokens.size() - 1;//ugly, rewrite
        while (getTokenInfoByRelativeNumber(lastNonWsIndex).isWhitespace() &&
                fakeStartMarker.getStartOffset() < getTokenInfoByRelativeNumber(lastNonWsIndex).getTokenStart()) {
          --lastNonWsIndex;
        }

        int newEofTokenIndex = filteredTokens.get(lastNonWsIndex) + 1;

        while (newEofTokenIndex < originalTokens.size()) {
          if (!originalTokens.get(newEofTokenIndex).isWhitespace()) {
            backStepToken = originalTokens.get(newEofTokenIndex);
            backStepNumber = newEofTokenIndex;
            backStepToken.addForeignProductionMarker(endMarker);
            return;
          }

          ++newEofTokenIndex;
        }
      }

      currentTokenInfo.addProductionMarker(endMarker);
    }

    private void collapse(FakeMarker marker, IElementType astElementType) {
      FakeStartMarker startMarker = (FakeStartMarker) marker;
      if (!checkStartMarker(startMarker, "Attempt to .collapse() invalid marker")) return;

      while (lastMarker != null && lastMarker != marker) {
        lastMarker.setValid(false);
        lastMarker = lastMarker.getPrevMarker();
      }

      done(marker, astElementType, null);
    }

    /**
     * :( 
     */
    private void doneOrErrorBefore(IElementType astElementType, FakeStartMarker doneMarker, FakeStartMarker beforeMarker,
                                   @Nullable String errorMessage) {
      astElementType = remapAstElement(astElementType);
      if (!checkEndMarker(doneMarker, astElementType)) return;
      if (!checkStartMarker(beforeMarker, "Attempt to .doneBefore() on invalid before-marker")) return;
      if (!checkStartMarker(doneMarker, "Attempt to .doneBefore() on invalid marker")) return;

      int beforeTokenNum = beforeMarker.getMyTokenNum();

      if (doneMarker.getMyTokenNum() > beforeTokenNum) {
        logError("Attempt to done next marker before previous marker");
        return;
      }

      if (beforeMarker.getMyTokenNum() > 0) {
        for (int i = filteredTokens.get(beforeTokenNum - 1) + 1; i < filteredTokens.get(beforeTokenNum); ++i) {
          if (!originalTokens.get(i).isWhitespace()) {
            beforeTokenNum = i;
            break;
          }
        }
      }

      FakeEndMarker endMarker = createEndMarker(doneMarker, astElementType, beforeTokenNum, errorMessage);
      insertMarkerBefore(endMarker, beforeMarker);

      final List<FakeMarker> allMarkers = getTokenInfoByRelativeNumber(beforeMarker.getMyTokenNum()).getAllMarkers();
      if (beforeTokenNum == beforeMarker.getMyTokenNum() || (allMarkers != null && allMarkers.get(0) != beforeMarker)) {
        getTokenInfoByRelativeNumber(beforeTokenNum).addProductionMarker(endMarker);
        return;
      }

      originalTokens.get(beforeTokenNum).addForeignProductionMarker(endMarker);
    }

    private void doneBefore(IElementType astElementType, FakeStartMarker doneMarker, FakeStartMarker beforeMarker,
                            String errorMessage) {
      doneOrErrorBefore(astElementType, doneMarker, beforeMarker, null);
      final FakeErrorMarker errorMarker = new FakeErrorMarker(errorMessage);
      getValidTokenInfo(beforeMarker.getMyTokenNum() - 1).setMyErrorMarker(errorMarker);
      insertMarkerBefore(errorMarker, beforeMarker.getPrevMarker());
    }

    private void doneWithError(FakeStartMarker marker, String errorMessage) {
      done(marker, TokenType.ERROR_ELEMENT, errorMessage);
    }

    private void errorBefore(FakeStartMarker marker, FakeStartMarker beforeMarker, String errorMessage) {
      doneOrErrorBefore(TokenType.ERROR_ELEMENT, marker, beforeMarker, errorMessage);
    }

    @SuppressWarnings("UnusedDeclaration")
    private abstract class FakeMarker implements PsiBuilder.Marker {
      private FakeMarker prevMarker;
      private FakeMarker nextMarker;
      private boolean isValid = true;


      private StackTraceElement[] placeOfCreation;

      FakeMarker() {
        if (LayeredParserPsiBuilder.this.isDebugMode) {
          setPlace(new Exception().getStackTrace());
        }
      }

      public void drop() {
        throw new UnsupportedOperationException();
      }

      @NotNull
      public PsiBuilder.Marker precede() {
        throw new UnsupportedOperationException();
      }

      public void rollbackTo() {
        throw new UnsupportedOperationException();
      }

      public void done(@NotNull IElementType type) {
        throw new UnsupportedOperationException();
      }

      public void collapse(@NotNull IElementType type) {
        throw new UnsupportedOperationException();
      }

      public void doneBefore(@NotNull IElementType type, @NotNull Marker before) {
        throw new UnsupportedOperationException();
      }

      public void doneBefore(@NotNull IElementType type, @NotNull PsiBuilder.Marker before, String errorMessage) {
        throw new UnsupportedOperationException();
      }

      public void error(String message) {
        throw new UnsupportedOperationException();
      }

      public void errorBefore(String message, @NotNull PsiBuilder.Marker before) {
        throw new UnsupportedOperationException();
      }

      public int getStartOffset() {
        throw new UnsupportedOperationException();
      }

      public void setCustomEdgeTokenBinders(@Nullable WhitespacesAndCommentsBinder left,
                                            @Nullable WhitespacesAndCommentsBinder right) {
        throw new UnsupportedOperationException();
      }

      FakeMarker getPrevMarker() {
        return prevMarker;
      }

      void setPrevMarker(FakeMarker prevMarker) {
        this.prevMarker = prevMarker;
      }

      FakeMarker getNextMarker() {
        return nextMarker;
      }

      void setNextMarker(FakeMarker nextMarker) {
        this.nextMarker = nextMarker;
      }

      public boolean isValid() {
        return isValid;
      }

      public void setValid(boolean valid) {
        isValid = valid;
      }

      public void setPlace(StackTraceElement[] place) {
        placeOfCreation = place;
      }
    }

    private class FakeStartMarker extends FakeMarker {
      private final int myStart;
      private final int myTokenNum;
      private FakeEndMarker endMarker;
      private Marker delegateMarker;
      private Pair<WhitespacesAndCommentsBinder, WhitespacesAndCommentsBinder> myCustomEdgeTokenBinders;
      private StackTraceElement[] createdAt;


      private FakeStartMarker(int myStart, int myTokenNum) {
        if (isDebug) {
          createdAt = new Exception().getStackTrace();
        }
        this.myStart = myStart;
        this.myTokenNum = myTokenNum;
      }

      public StackTraceElement[] getCreatedAt() {
        return createdAt;
      }

      public int getStartOffset() {
        return myStart;
      }

      int getMyTokenNum() {
        return myTokenNum;
      }

      @Override
      public void drop() {
        setValid(false);
      }

      @Override
      public void rollbackTo() {
        LayeredParserPsiBuilder.this.rollbackTo(this);
      }

      @NotNull
      @Override
      public Marker precede() {
        return LayeredParserPsiBuilder.this.precede(this);
      }

      @Override
      public void done(@NotNull IElementType type) {
        LayeredParserPsiBuilder.this.done(this, type, null);
      }

      @Override
      public void collapse(@NotNull IElementType type) {
        LayeredParserPsiBuilder.this.collapse(this, type);
      }

      @Override
      public void doneBefore(@NotNull IElementType type, @NotNull Marker before) {
        LayeredParserPsiBuilder.this.doneOrErrorBefore(type, this, (FakeStartMarker) before, null);
      }

      @Override
      public void doneBefore(@NotNull IElementType type, @NotNull Marker before, String errorMessage) {
        LayeredParserPsiBuilder.this.doneBefore(type, this, (FakeStartMarker) before, errorMessage);
      }

      @Override
      public void error(String message) {
        LayeredParserPsiBuilder.this.doneWithError(this, message);
      }

      @Override
      public void errorBefore(String message, @NotNull Marker before) {
        LayeredParserPsiBuilder.this.errorBefore(this, (FakeStartMarker) before, message);
      }

      Marker getDelegateMarker() {
        return delegateMarker;
      }

      void setDelegateMarker(Marker delegateMarker) {
        this.delegateMarker = delegateMarker;
      }

      Pair<WhitespacesAndCommentsBinder, WhitespacesAndCommentsBinder> getMyCustomEdgeTokenBinders() {
        return myCustomEdgeTokenBinders;
      }

      @Override
      public void setCustomEdgeTokenBinders(@Nullable WhitespacesAndCommentsBinder left, @Nullable WhitespacesAndCommentsBinder right) {
        myCustomEdgeTokenBinders = new Pair<WhitespacesAndCommentsBinder, WhitespacesAndCommentsBinder>(left, right);
      }

      @Override
      public String toString() {
        return "FakeStartMarker [TextOffset:  " + getStartOffset() + " ; TokenNumber: " + getMyTokenNum() + " ; MyDelegate: " + delegateMarker + "]";
      }

      public FakeEndMarker getEndMarker() {
        return endMarker;
      }

      public void setEndMarker(FakeEndMarker endMarker) {
        this.endMarker = endMarker;
      }
    }

    private class FakeEndMarker extends FakeMarker implements LighterASTNode {
      private FakeStartMarker startMarker;
      private final IElementType astElementType;
      private int endOffset;

      private FakeEndMarker(FakeStartMarker startMarker, IElementType astElementType, int endOffset) {
        this.startMarker = startMarker;
        this.astElementType = astElementType;
        this.endOffset = endOffset;
      }

      @Override
      public IElementType getTokenType() {
        return astElementType;
      }

      @Override
      public int getStartOffset() {
        return getStartMarker().getStartOffset();
      }

      @Override
      public int getEndOffset() {
        return endOffset;
      }

      public FakeStartMarker getStartMarker() {
        return startMarker;
      }

      public void setEndOffset(int endOffset) {
        this.endOffset = endOffset;
      }

      public void doneMarker() {
        if (!checkOnDone()) return;
        getStartMarker().getDelegateMarker().done(getTokenType());
      }

      @Override
      public String toString() {
        return "FakeEndMarker  [TextStartOffset: " + getStartOffset() + " ; TextEndOffset: " + getEndOffset() +
                " ; IElementType: " + getTokenType() + "]";
      }

      boolean checkOnDone() {
        if (getStartMarker().getDelegateMarker() == null) {
          logError("Attempt to .done marker" + toString() + " before .copyWithRoot");
          return false;
        }

        return true;
      }
    }

    private class FakeErrorMarker extends FakeMarker {
      private final String message;

      private FakeErrorMarker(String message) {
        this.message = message;
      }

      public String getMessage() {
        return message;
      }
    }

    private class FakeEndWithErrorMarker extends FakeEndMarker {
      private final String errorMessage;

      private FakeEndWithErrorMarker(FakeStartMarker startMarker, IElementType astElementType, int endOffset,
                                     String errorMessage) {
        super(startMarker, astElementType, endOffset);
        this.errorMessage = errorMessage;
      }

      public String getErrorMessage() {
        return errorMessage;
      }

      @Override
      public void doneMarker() {
        if (!checkOnDone()) return;
        getStartMarker().getDelegateMarker().error(getErrorMessage());
      }
    }
  }
  
  public interface ContentsParser {
    void parseContents(ASTNode chameleon, PsiBuilder builder);
  }

  /**
   * Can be stateful 
   */
  public interface CustomTokenChooser {
    /**
     * Processes the next token type. This token type will be then passed to 
     * {@link CustomTokenChooser#shouldSelectForeignToken(com.intellij.psi.tree.IElementType)} 
     */
    void processToken(IElementType tokenType);

    /**
     * Don't put your filtering logic in these methods as it won't be called if tokenType is already of a 
     * suitable class or already rejected.  
     */
    boolean shouldSelectForeignToken(IElementType tokenType);
    boolean shouldRejectOwnToken(IElementType tokenType);
  }

  public interface ConflictResolver {
    boolean needDoBackStep(IElementType backStepTokenType, IElementType astDoneElementType, 
                           IElementType currentElementType, CharSequence tokenText);
  }

  private class DefaultConflictResolver implements ConflictResolver {
    public boolean needDoBackStep(IElementType backStepTokenType, IElementType astDoneElementType, 
                                  IElementType currentElementType, CharSequence tokenText) {
      return !("}".equals(tokenText) || ">".equals(tokenText) || "]".equals(tokenText) || ")".equals(tokenText));
    }
  }
  
  public abstract class RegisteredInfo<E, T> {
    protected final E myParser;
    final List<Class<? extends IElementType>> myRegisteredTokens;
    final StateFlusher myStateFlusher;
    private final AstElementRemapper myElementRemapper;
    final T myReferenceElement;
    final TokenSet myIgnoredTokens;

    CustomTokenChooser myTokenChooser;

    RegisteredInfo(@NotNull E myParser, @NotNull List<Class<? extends IElementType>> myRegisteredTokens,
                   @Nullable StateFlusher myStateFlusher, @Nullable T myReferenceElement,
                   @Nullable TokenSet myIgnoredTokens, @Nullable AstElementRemapper elementRemapper) {
      this.myParser = myParser;
      this.myRegisteredTokens = myRegisteredTokens;
      this.myStateFlusher = myStateFlusher == null? new NullStateFlusher() : myStateFlusher;
      this.myIgnoredTokens = myIgnoredTokens == null? TokenSet.EMPTY : myIgnoredTokens;
      this.myReferenceElement = myReferenceElement;
      this.myElementRemapper = elementRemapper == null? new NullAstElementRemapper() : elementRemapper;
    }

    public E getMyParser() {
      return myParser;
    }

    List<Class<? extends IElementType>> getMyRegisteredTokens() {
      return myRegisteredTokens;
    }

    StateFlusher getMyStateFlusher() {
      return myStateFlusher;
    }
    
    T getMyReferenceElement() {
      return myReferenceElement;
    }
    
    public boolean isIgnored(IElementType elementType) {
      return myIgnoredTokens.contains(elementType);
    }
    
    AstElementRemapper getMyElementRemapper() {
      return myElementRemapper;
    }

    public RegisteredInfo<E, T> setTokenChooser(CustomTokenChooser myTokenChooser) {
      this.myTokenChooser = myTokenChooser;
      return this;
    }
    
    boolean mustTakeForeignToken(IElementType tokenType) {
      return myTokenChooser != null && myTokenChooser.shouldSelectForeignToken(tokenType);
    }
    
    boolean mustRejectOwnToken(IElementType tokenType) {
      return myTokenChooser != null && myTokenChooser.shouldRejectOwnToken(tokenType);
    }
    
    void processNextTokenWithChooser(IElementType tokenType) {
      if (myTokenChooser != null) myTokenChooser.processToken(tokenType);
    }

    abstract void parse(@NotNull PsiBuilder builder);
  }
  
  public class RegisteredParserInfo extends RegisteredInfo<PsiParser, IElementType> {
    public RegisteredParserInfo(PsiParser myParser, List<Class<? extends IElementType>> myRegisteredTokens, 
                                   StateFlusher myStateFlusher, IElementType myReferenceElement, 
                                   TokenSet myIgnoredTokens, AstElementRemapper remapper) {
      super(myParser, myRegisteredTokens, myStateFlusher, myReferenceElement, myIgnoredTokens, remapper);
    }

    @Override
    void parse(@NotNull PsiBuilder builder) {
      getMyParser().parse(getMyReferenceElement(), builder);
    }
  }
  
  public class RegisteredContentsParserInfo extends RegisteredInfo<ContentsParser, ASTNode> {
    public RegisteredContentsParserInfo(ContentsParser myParser, List<Class<? extends IElementType>> myRegisteredTokens, 
                                           StateFlusher myStateFlusher, ASTNode myReferenceElement, 
                                           TokenSet myIgnoredTokens, AstElementRemapper remapper) {
      super(myParser, myRegisteredTokens, myStateFlusher, myReferenceElement, myIgnoredTokens, remapper);
    }

    @Override
    void parse(@NotNull PsiBuilder builder) {
      getMyParser().parseContents(getMyReferenceElement(), builder);
    }
  }
  
  interface StateFlusher {
    boolean isFlushOnBuilderNeeded(IElementType currentTokenType);
  }
  
  public interface AstElementRemapper {
    IElementType remap(IElementType astElementType);
  }
  
  private class NullStateFlusher implements StateFlusher {
    public boolean isFlushOnBuilderNeeded(IElementType currentTokenType) {
      return false;
    }

  }
  
  private class NullAstElementRemapper implements AstElementRemapper {
    @Override
    public final IElementType remap(IElementType astElementType) {
      return astElementType;
    }
  }

  private class BufferedTokenInfo {
    private IElementType tokenType;
    private final int tokenEnd;
    private final boolean isWhitespace;
    private final int tokenStart;
    
    private int foreignMarkerEdge;
    
    private LinkedList<LayeredParserPsiBuilder.FakeMarker> myProductionMarkerList;
    private LayeredParserPsiBuilder.FakeErrorMarker myErrorMarker;
    

    private BufferedTokenInfo(IElementType tokenType, boolean whitespace, int tokenStart, int tokenEnd) {
      this.tokenType = tokenType;
      this.tokenEnd = tokenEnd;
      isWhitespace = whitespace;
      this.tokenStart = tokenStart;
      foreignMarkerEdge = 0;
    }

    public IElementType getTokenType() {
      return tokenType;
    }

    public void setTokenType(@NotNull IElementType newTokenType) {
      tokenType = newTokenType;
    }
    
    public int getTokenEnd() {
      return tokenEnd;
    }

    public boolean isWhitespace() {
      return isWhitespace;
    }

    public int getTokenStart() {
      return tokenStart;
    }
    
    void addForeignProductionMarker(LayeredParserPsiBuilder.FakeMarker productionMarker) {
      ensureProduction();
//      if (foreignMarkerEdge < myProductionMarkerList.size()) {
//        insertMarkerBefore(productionMarker, myProductionMarkerList.get(foreignMarkerEdge));
//      }
      myProductionMarkerList.add(foreignMarkerEdge++, productionMarker);
    }
    
    void addProductionMarker(LayeredParserPsiBuilder.FakeMarker productionMarker) {
      ensureProduction();
      myProductionMarkerList.add(productionMarker);
    }

    void addProductionMarkerBefore(LayeredParserPsiBuilder.FakeMarker markerToAdd, LayeredParserPsiBuilder.FakeMarker before) {
      ensureProduction();
      int indx = myProductionMarkerList.lastIndexOf(before);
      
      if (indx == -1) {
        logError("Attempt to .addBefore missing marker:\n " +
            "marker to add: " + markerToAdd + "\n" +
            "before marker: " + before);
        myProductionMarkerList.add(markerToAdd);
        return;
      }
      myProductionMarkerList.add(indx, markerToAdd);
    }

    List<LayeredParserPsiBuilder.FakeMarker> getAllMarkers() {
      return myProductionMarkerList;
    }

    LayeredParserPsiBuilder.FakeErrorMarker getMyErrorMarker() {
      return myErrorMarker;
    }

    void setMyErrorMarker(LayeredParserPsiBuilder.FakeErrorMarker myErrorMarker) {
      this.myErrorMarker = myErrorMarker;
    }
    
    boolean hasMarkers() {
      if (getMyErrorMarker() != null) return true;
      if (getAllMarkers() == null) return false;
      
      for (LayeredParserPsiBuilder.FakeMarker marker : getAllMarkers()) {
        if (marker.isValid()) return true;
      }
      
      return false;
    }
    
    private void ensureProduction() {
      if (myProductionMarkerList == null) myProductionMarkerList = new LinkedList<LayeredParserPsiBuilder.FakeMarker>();
    }
  }

  private void insertMarkerBefore(LayeredParserPsiBuilder.FakeMarker markerToInsert, 
                                  LayeredParserPsiBuilder.FakeMarker beforeMarker) {
    markerToInsert.setNextMarker(beforeMarker);
    markerToInsert.setPrevMarker(beforeMarker.getPrevMarker());
    if (beforeMarker.getPrevMarker() != null) beforeMarker.getPrevMarker().setNextMarker(markerToInsert);
    beforeMarker.setPrevMarker(markerToInsert);
  }
}
