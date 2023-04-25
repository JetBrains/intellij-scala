/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.scala.editor.mouseHandler;

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.AbstractDocumentationTooltipAction;
import com.intellij.codeInsight.navigation.DocPreviewUtil;
import com.intellij.ide.PowerSaveMode;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.actions.ScalaExpressionTypeProvider;
import org.jetbrains.plugins.scala.editor.ScalaEditorBundle;
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings;
import org.jetbrains.plugins.scala.util.UnloadAwareDisposable;
import scala.Option;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;

//todo: move changes to CtrlMouseHandler!!! This is ok, for IDEA 12, but it's not ok for IDEA 13, where it's possible to patch.
public class MouseHoverHandler implements StartupActivity.DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    final var mouseHoverListeners = new MouseHoverHandlerListeners(project);
    mouseHoverListeners.registerListeners();
  }

  private static class MouseHoverHandlerListeners {

    private static final AbstractDocumentationTooltipAction[] ourTooltipActions = {new ShowQuickDocAtPinnedWindowFromTooltipAction()};
    @Nullable
    private TooltipProvider myTooltipProvider = null;
    @SuppressWarnings("removal")
    private final com.intellij.codeInsight.documentation.DocumentationManager myDocumentationManager;
    @Nullable
    private Point myPrevMouseLocation;
    @Nullable
    private volatile Point myMouseLocationOnHintActivation;
    @Nullable
    private LightweightHint myHint;
    private final Project myProject;
    private final Disposable myProjectDisposable;

    private enum BrowseMode {None, Hover}

    private final EditorMouseListener myEditorMouseAdapter = new EditorMouseListener() {
      @Override
      public void mouseReleased(@NotNull EditorMouseEvent e) {
        myTooltipAlarm.cancelAllRequests();
        myTooltipProvider = null;
      }
    };

    private final EditorMouseMotionListener myEditorMouseMotionListener = new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull final EditorMouseEvent e) {
        if (e.isConsumed() || !myProject.isInitialized()) {
          return;
        }

        MouseEvent mouseEvent = e.getMouseEvent();
        Point mousePosition = new Point(mouseEvent.getPoint());

        if (isMouseOverTooltip(mouseEvent.getLocationOnScreen())
                || ScreenUtil.isMovementTowards(myPrevMouseLocation, mouseEvent.getLocationOnScreen(), getHintBounds())) {
          myPrevMouseLocation = mouseEvent.getLocationOnScreen();
          return;
        }
        myPrevMouseLocation = mouseEvent.getLocationOnScreen();

        Point hintsMouseActivationLocation = myMouseLocationOnHintActivation;
        if (hintsMouseActivationLocation != null && hintsMouseActivationLocation.distance(mousePosition) < 5) {
          return;
        }

        if (myHint != null) {
          myHint.hide();
          myHint = null;
          myMouseLocationOnHintActivation = null;
        }

        Editor editor = e.getEditor();
        if (editor.getProject() != null && editor.getProject() != myProject)
          return;

        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (virtualFile instanceof LightVirtualFile)
          return;

        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

        if (psiFile != null && psiFile.getViewProvider().getBaseLanguage() != ScalaLanguage.INSTANCE) {
          PsiFile scalaFile = psiFile.getViewProvider().getPsi(ScalaLanguage.INSTANCE);
          if (scalaFile == null)
            return;
        }

        final LogicalPosition pos = editor.xyToLogicalPosition(mousePosition);
        int offset = editor.logicalPositionToOffset(pos);
        int selStart = editor.getSelectionModel().getSelectionStart();
        int selEnd = editor.getSelectionModel().getSelectionEnd();

        int myStoredModifiers = mouseEvent.getModifiersEx();
        final BrowseMode browseMode = getBrowseMode(myStoredModifiers);

        if (myTooltipProvider != null) {
          myTooltipProvider.dispose();
        }

        if (browseMode == BrowseMode.None || offset >= selStart && offset < selEnd) {
          myTooltipAlarm.cancelAllRequests();
          myTooltipProvider = null;
          return;
        }

        myTooltipAlarm.cancelAllRequests();
        final Editor finalEditor = editor;
        myTooltipAlarm.addRequest(
                () -> {
                  if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return;
                  myTooltipProvider = new TooltipProvider(finalEditor, pos, mousePosition);
                  myTooltipProvider.execute(browseMode);
                },
                ScalaCompileServerSettings.getInstance().SHOW_TYPE_TOOLTIP_DELAY
        );
      }
    };

    private final CaretListener myCaretListener = new CaretListener() {
      @Override
      @SuppressWarnings("removal")
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (myHint != null) {
          myDocumentationManager.updateToolwindowContext();
        }
      }
    };

    @NotNull
    private final Alarm myDocAlarm;
    @NotNull
    private final Alarm myTooltipAlarm;

    @SuppressWarnings("removal")
    public MouseHoverHandlerListeners(final Project project) {
      myProject = project;
      myProjectDisposable = UnloadAwareDisposable.forProject(myProject);
      myDocumentationManager = com.intellij.codeInsight.documentation.DocumentationManager.getInstance(project);;
      myDocAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProjectDisposable);
      myTooltipAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myProjectDisposable);
    }

    public void registerListeners() {
      EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      eventMulticaster.addEditorMouseListener(myEditorMouseAdapter, myProjectDisposable);
      eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener, myProjectDisposable);
      eventMulticaster.addCaretListener(myCaretListener, myProjectDisposable);
    }

    private boolean isMouseOverTooltip(@NotNull Point mouseLocationOnScreen) {
      Rectangle bounds = getHintBounds();
      return bounds != null && bounds.contains(mouseLocationOnScreen);
    }

    @Nullable
    private Rectangle getHintBounds() {
      LightweightHint hint = myHint;
      if (hint == null) {
        return null;
      }
      JComponent hintComponent = hint.getComponent();
      if (hintComponent == null || !hintComponent.isShowing()) {
        return null;
      }
      Rectangle bounds = new Rectangle(hintComponent.getLocationOnScreen(), hintComponent.getSize());
      // grow the bounds so that it easier to stay on the tooltip
      bounds.grow(13, 13);
      return bounds;
    }

    private static BrowseMode getBrowseMode(int modifiers) {
      if (modifiers == 0) {
        return BrowseMode.Hover;
      }
      return BrowseMode.None;
    }

    @NotNull
    private static DocInfo generateInfo(PsiElement atPointer, String result) {
      return result == null ? DocInfo.EMPTY : new DocInfo(result, null, null);
    }

    private abstract static class Info {
      @NotNull
      protected final PsiElement myElementAtPointer;
      private final List<TextRange> myRanges;

      public Info(@NotNull PsiElement elementAtPointer, List<TextRange> ranges) {
        myElementAtPointer = elementAtPointer;
        myRanges = ranges;
      }

      public Info(@NotNull PsiElement elementAtPointer) {
        this(elementAtPointer, Collections.singletonList(new TextRange(elementAtPointer.getTextOffset(),
                elementAtPointer.getTextOffset() + elementAtPointer.getTextLength())));
      }

      public List<TextRange> getRanges() {
        return myRanges;
      }

      @NotNull
      public abstract DocInfo getInfo();

      public abstract boolean isValid(Document document);

      public abstract void showDocInfo(@SuppressWarnings("removal") @NotNull com.intellij.codeInsight.documentation.DocumentationManager docManager);

      protected boolean rangesAreCorrect(Document document) {
        final TextRange docRange = new TextRange(0, document.getTextLength());
        for (TextRange range : getRanges()) {
          if (!docRange.contains(range)) return false;
        }

        return true;
      }
    }

    private static void showDumbModeNotification(final Project project) {
      DumbService.getInstance(project).showDumbModeNotification(ScalaEditorBundle.message("element.information.is.not.available.during.index.update"));
    }

    private static class InfoSingle extends Info {
      private final String result;

      public InfoSingle(@NotNull PsiElement elementAtPointer, String result) {
        super(elementAtPointer);
        this.result = result;
      }

      @Override
      @NotNull
      public DocInfo getInfo() {
        try {
          return ReadAction.compute((ThrowableComputable<DocInfo, IndexNotReadyException>) () ->
                  generateInfo(myElementAtPointer, result)
          );
        }
        catch (IndexNotReadyException e) {
          showDumbModeNotification(myElementAtPointer.getProject());
          return DocInfo.EMPTY;
        }
      }

      @Override
      public boolean isValid(Document document) {
        return myElementAtPointer.isValid() && rangesAreCorrect(document);

      }

      @Override
      @SuppressWarnings("removal")
      public void showDocInfo(@NotNull com.intellij.codeInsight.documentation.DocumentationManager docManager) {
//      docManager.showJavaDocInfo(myTargetElement, myElementAtPointer, true, null);
        docManager.setAllowContentUpdateFromContext(false);
      }
    }

    @Nullable
    private Info getInfoAt(final Editor editor, PsiFile file, int offset, BrowseMode browseMode) {
      if (browseMode == BrowseMode.Hover) {
        if (!isShowTooltip())
          return null;
        if (file instanceof ScalaFile) {
          PsiElement elementAtPointer = file.findElementAt(offset);
          if (elementAtPointer == null)
            return null;

          // When offset is the end-offset of the identifier, the next element will be found
          // so if that happens, we just use the left leaf sibling if that is the identifier
          elementAtPointer = ScalaPsiUtil.adjustElementAtOffset(elementAtPointer, offset, true);

          Option<String> typeInfoHint = ScalaExpressionTypeProvider.getTypeInfoHint(elementAtPointer);
          if (typeInfoHint.isDefined()) {
            String hint = typeInfoHint.get();
            return new InfoSingle(elementAtPointer, hint);
          }
        }
      }

      return null;
    }

    private boolean isShowTooltip() {
      return !PowerSaveMode.isEnabled() &&
              ScalaCompileServerSettings.getInstance().SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER;
    }

    private void fulfillDocInfo(@NotNull final String header,
                                @NotNull final DocumentationProvider provider,
                                @NotNull final PsiElement originalElement,
                                @NotNull final PsiElement anchorElement,
                                @NotNull final Consumer<? super String> newTextConsumer,
                                @NotNull final LightweightHint hint)
    {
      myDocAlarm.cancelAllRequests();
      myDocAlarm.addRequest(() -> {
        final Ref<String> fullTextRef = new Ref<String>();
        final Ref<String> qualifiedNameRef = new Ref<String>();
        ApplicationManager.getApplication().runReadAction(() -> {
          try {
            fullTextRef.set(provider.generateDoc(anchorElement, originalElement));
          }
          catch (IndexNotReadyException e) {
            fullTextRef.set(ScalaEditorBundle.message("documentation.is.not.available.while.indexing"));
          }
          if (anchorElement instanceof PsiQualifiedNamedElement) {
            qualifiedNameRef.set(((PsiQualifiedNamedElement)anchorElement).getQualifiedName());
          }
        });
        String fullText = fullTextRef.get();
        if (fullText == null) {
          return;
        }
        final String updatedText = DocPreviewUtil.buildPreview(header, qualifiedNameRef.get(), fullText);
        final String newHtml = HintUtil.prepareHintText(updatedText, HintUtil.getInformationHint());
        UIUtil.invokeLaterIfNeeded(() -> {

          // There is a possible case that quick doc control width is changed, e.g. it contained text
          // like 'public final class String implements java.io.Serializable, java.lang.Comparable<java.lang.String>' and
          // new text replaces fully-qualified class names by hyperlinks with short name.
          // That's why we might need to update the control size. We assume that the hint component is located at the
          // layered pane, so, the algorithm is to find an ancestor layered pane and apply new size for the target component.

          JComponent component = hint.getComponent();
          Dimension oldSize = component.getPreferredSize();
          newTextConsumer.consume(newHtml);

          final int widthIncrease;
          if (component instanceof QuickDocInfoPane) {
            int buttonWidth = ((QuickDocInfoPane)component).getButtonWidth();
            widthIncrease = calculateWidthIncrease(buttonWidth, updatedText);
          }
          else {
            widthIncrease = 0;
          }

          if (oldSize == null) {
            return;
          }

          Dimension newSize = component.getPreferredSize();
          if (newSize.width + widthIncrease == oldSize.width) {
            return;
          }
          component.setPreferredSize(new Dimension(newSize.width + widthIncrease, newSize.height));

          // We're assuming here that there are two possible hint representation modes: popup and layered pane.
          if (hint.isRealPopup()) {

            TooltipProvider tooltipProvider = myTooltipProvider;
            if (tooltipProvider != null) {
              // There is a possible case that 'raw' control was rather wide but the 'rich' one is narrower. That's why we try to
              // re-show the hint here. Benefits: there is a possible case that we'll be able to show nice layered pane-based balloon;
              // the popup will be re-positioned according to the new width.
              hint.hide();
              tooltipProvider.showHint(new LightweightHint(component));
            }
            else {
              component.setPreferredSize(new Dimension(newSize.width + widthIncrease, oldSize.height));
              hint.pack();
            }
            return;
          }

          Container topLevelLayeredPaneChild = null;
          boolean adjustBounds = false;
          for (Container current = component.getParent(); current != null; current = current.getParent()) {
            if (current instanceof JLayeredPane) {
              adjustBounds = true;
              break;
            }
            else {
              topLevelLayeredPaneChild = current;
            }
          }

          if (adjustBounds && topLevelLayeredPaneChild != null) {
            Rectangle bounds = topLevelLayeredPaneChild.getBounds();
            topLevelLayeredPaneChild.setBounds(bounds.x, bounds.y, bounds.width + newSize.width + widthIncrease - oldSize.width, bounds.height);
          }
        });
      }, 0);
    }

    /**
     * It's possible that we need to expand quick doc control's width in order to provide better visual representation
     * (see https://youtrack.jetbrains.com/issue/IDEA-101425). This method calculates that width expand.
     *
     * @param buttonWidth  icon button's width
     * @param updatedText  text which will be should at the quick doc control
     * @return             width increase to apply to the target quick doc control (zero if no additional width increase is required)
     */
    private static int calculateWidthIncrease(int buttonWidth, String updatedText) {
      int maxLineWidth = 0;
      final var lineWidths = new IntArrayList();
      for (String lineText : StringUtil.split(updatedText, "<br/>")) {
        String html = HintUtil.prepareHintText(lineText, HintUtil.getInformationHint());
        int width = new JLabel(html).getPreferredSize().width;
        maxLineWidth = Math.max(maxLineWidth, width);
        lineWidths.add(width);
      }

      if (!lineWidths.isEmpty()) {
        int firstLineAvailableTrailingWidth = maxLineWidth - lineWidths.getInt(0);
        if (firstLineAvailableTrailingWidth >= buttonWidth) {
          return 0;
        }
        else {
          return buttonWidth - firstLineAvailableTrailingWidth;
        }
      }
      return 0;
    }

    private class TooltipProvider {
      private final Editor myEditor;
      private final LogicalPosition myPosition;
      private final Point myMousePosition;
      private BrowseMode myBrowseMode;
      private boolean myDisposed;

      public TooltipProvider(Editor editor, LogicalPosition pos, Point mousePosition) {
        myEditor = editor;
        myPosition = pos;
        myMousePosition = mousePosition;
      }

      public void dispose() {
        myDisposed = true;
      }

      public void execute(BrowseMode browseMode) {
        if (myEditor.isDisposed())
          return;

        myBrowseMode = browseMode;

        Document document = myEditor.getDocument();
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (file == null)
          return;

        if (myEditor.isDisposed() || EditorUtil.inVirtualSpace(myEditor, myPosition)) {
          return;
        }

        final int offset = myEditor.logicalPositionToOffset(myPosition);

        int selStart = myEditor.getSelectionModel().getSelectionStart();
        int selEnd = myEditor.getSelectionModel().getSelectionEnd();

        if (offset >= selStart && offset < selEnd)
          return;

        ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressIndicatorUtils.scheduleWithWriteActionPriority(new ReadTask() {
          @Override
          public void computeInReadAction(@NotNull ProgressIndicator indicator) {
            doExecute(file, offset);
          }

          @Override
          public void onCanceled(@NotNull ProgressIndicator indicator) {}
        }));
      }

      private void doExecute(PsiFile file, int offset) {
        final Info info;
        try {
          info = getInfoAt(myEditor, file, offset, myBrowseMode);
        }
        catch (IndexNotReadyException e) {
          showDumbModeNotification(myProject);
          return;
        }
        if (info == null)
          return;

        ApplicationManager.getApplication().invokeLater(() -> {
          if (myDisposed || myEditor.isDisposed() || !myEditor.getComponent().isShowing())
            return;
          showHint(info);
        });
      }

      private void showHint(Info info) {
        if (myDisposed || myEditor.isDisposed())
          return;

        if (!info.isValid(myEditor.getDocument())) {
          return;
        }

        DocInfo docInfo = info.getInfo();

        if (docInfo.text == null) return;

        if (myDocumentationManager.hasActiveDockedDocWindow()) {
          info.showDocInfo(myDocumentationManager);
        }

        HyperlinkListener hyperlinkListener = docInfo.docProvider == null
                ? null
                : new QuickDocHyperlinkListener(docInfo.docProvider, info.myElementAtPointer);
        final Ref<QuickDocInfoPane> quickDocPaneRef = new Ref<QuickDocInfoPane>();
        MouseListener mouseListener = new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            QuickDocInfoPane pane = quickDocPaneRef.get();
            if (pane != null) {
              pane.mouseEntered(e);
            }
          }

          @Override
          public void mouseExited(MouseEvent e) {
            QuickDocInfoPane pane = quickDocPaneRef.get();
            if (pane != null) {
              pane.mouseExited(e);
            }
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            QuickDocInfoPane pane = quickDocPaneRef.get();
            if (pane != null) {
              pane.mouseClicked(e);
            }
          }
        };
        Ref<Consumer<? super String>> newTextConsumerRef = new Ref<>();
        JComponent label = HintUtil.createInformationLabel(docInfo.text, hyperlinkListener, mouseListener, newTextConsumerRef);

        Consumer<? super String> newTextConsumer = newTextConsumerRef.get();
        QuickDocInfoPane quickDocPane = null;
        if (docInfo.documentationAnchor != null) {
          quickDocPane = new QuickDocInfoPane(docInfo.documentationAnchor, info.myElementAtPointer, label, docInfo.text);
          quickDocPaneRef.set(quickDocPane);
        }

        JComponent hintContent = quickDocPane == null ? label : quickDocPane;
        final LightweightHint hint = new LightweightHint(hintContent);
        myHint = hint;
        hint.addHintListener(new HintListener() {
          @Override
          public void hintHidden(@NotNull EventObject event) {
            myHint = null;
          }
        });
        myDocAlarm.cancelAllRequests();
        if (newTextConsumer != null && docInfo.docProvider != null && docInfo.documentationAnchor != null) {
          fulfillDocInfo(docInfo.text, docInfo.docProvider, info.myElementAtPointer, docInfo.documentationAnchor, newTextConsumer, hint);
        }

        showHint(hint);
      }

      public void showHint(LightweightHint hint) {
        final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
        Point p = HintManagerImpl.getHintPosition(hint, myEditor, myPosition, HintManager.ABOVE);

        hintManager.showEditorHint(hint, myEditor, p,
                HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING,
                0, false, HintManagerImpl.createHintHint(myEditor, p,  hint, HintManager.ABOVE).setContentActive(false));
        myMouseLocationOnHintActivation = myMousePosition;
      }
    }

    private static class DocInfo {

      public static final DocInfo EMPTY = new DocInfo(null, null, null);

      @Nullable
      public final String text;
      @Nullable
      public final DocumentationProvider docProvider;
      @Nullable
      public final PsiElement documentationAnchor;

      DocInfo(@Nullable String text, @Nullable DocumentationProvider provider, @Nullable PsiElement documentationAnchor) {
        this.text = text;
        docProvider = provider;
        this.documentationAnchor = documentationAnchor;
      }
    }

    private class QuickDocInfoPane extends JBLayeredPane {

      private static final int BUTTON_HGAP = 5;

      @NotNull
      private final List<JComponent> myButtons = new ArrayList<JComponent>();

      @NotNull
      private final JComponent myBaseDocControl;

      private final int myMinWidth;
      private final int myMinHeight;
      private final int myButtonWidth;

      QuickDocInfoPane(@NotNull PsiElement documentationAnchor,
                       @NotNull PsiElement elementUnderMouse,
                       @NotNull JComponent baseDocControl,
                       @NotNull String text)
      {
        myBaseDocControl = baseDocControl;

        PresentationFactory presentationFactory = new PresentationFactory();
        for (AbstractDocumentationTooltipAction action : ourTooltipActions) {
          Icon icon = action.getTemplatePresentation().getIcon();
          Dimension minSize = new Dimension(icon.getIconWidth(), icon.getIconHeight());
          myButtons.add(new ActionButton(action, presentationFactory.getPresentation(action), "ScalaQuickDocTooltip", minSize));
          action.setDocInfo(documentationAnchor, elementUnderMouse);
        }
        Collections.reverse(myButtons);

        setPreferredSize(baseDocControl.getPreferredSize());
        setMaximumSize(baseDocControl.getMaximumSize());
        setMinimumSize(baseDocControl.getMinimumSize());
        setBackground(baseDocControl.getBackground());

        add(baseDocControl, Integer.valueOf(0));
        int minWidth = 0;
        int minHeight = 0;
        int buttonWidth = 0;
        for (JComponent button : myButtons) {
          button.setBorder(null);
          button.setBackground(baseDocControl.getBackground());
          add(button, Integer.valueOf(1));
          button.setVisible(false);
          Dimension preferredSize = button.getPreferredSize();
          minWidth += preferredSize.width;
          minHeight = Math.max(minHeight, preferredSize.height);
          buttonWidth = Math.max(buttonWidth, preferredSize.width);
        }
        myButtonWidth = buttonWidth;

        int margin = 2;
        myMinWidth = minWidth + margin * 2 + (myButtons.size() - 1) * BUTTON_HGAP;
        myMinHeight = minHeight + margin * 2;
      }

      public int getButtonWidth() {
        return myButtonWidth;
      }

      @Override
      public Dimension getPreferredSize() {
        return expandIfNecessary(myBaseDocControl.getPreferredSize());
      }

      @Override
      public void setPreferredSize(Dimension preferredSize) {
        super.setPreferredSize(preferredSize);
        myBaseDocControl.setPreferredSize(preferredSize);
      }

      @Override
      public Dimension getMinimumSize() {
        return expandIfNecessary(myBaseDocControl.getMinimumSize());
      }

      @Override
      public Dimension getMaximumSize() {
        return expandIfNecessary(myBaseDocControl.getMaximumSize());
      }

      @NotNull
      private Dimension expandIfNecessary(@NotNull Dimension base) {
        if (base.width >= myMinWidth && base.height >= myMinHeight) {
          return base;
        }
        return new Dimension(Math.max(myMinWidth, base.width), Math.max(myMinHeight, base.height));
      }

      @Override
      public void doLayout() {
        Rectangle bounds = getBounds();
        myBaseDocControl.setBounds(new Rectangle(0, 0, bounds.width, bounds.height));

        int x = bounds.width;
        for (JComponent button : myButtons) {
          Dimension buttonSize = button.getPreferredSize();
          x -= buttonSize.width;
          button.setBounds(x, 0, buttonSize.width, buttonSize.height);
          x -= BUTTON_HGAP;
        }
      }

      public void mouseEntered(@NotNull MouseEvent e) {
        processStateChangeIfNecessary(e.getLocationOnScreen(), true);
      }

      public void mouseExited(@NotNull MouseEvent e) {
        processStateChangeIfNecessary(e.getLocationOnScreen(), false);
      }

      public void mouseClicked(@NotNull MouseEvent e) {
        // TODO den check the processing.
        int i = 1;
      }

      private void processStateChangeIfNecessary(@NotNull Point mouseScreenLocation, boolean mouseEntered) {
        // Don't show 'view quick doc' buttons if docked quick doc control is already active.
        if (myDocumentationManager.hasActiveDockedDocWindow()) {
          return;
        }

        // Skip event triggered when mouse leaves action button area.
        if (!mouseEntered && new Rectangle(getLocationOnScreen(), getSize()).contains(mouseScreenLocation)) {
          return;
        }
        for (JComponent button : myButtons) {
          button.setVisible(mouseEntered);
        }
      }
    }

    private class QuickDocHyperlinkListener implements HyperlinkListener {

      @NotNull
      private final DocumentationProvider myProvider;
      @NotNull
      private final PsiElement myContext;

      QuickDocHyperlinkListener(@NotNull DocumentationProvider provider, @NotNull PsiElement context) {
        myProvider = provider;
        myContext = context;
      }

      @Override
      @SuppressWarnings("removal")
      public void hyperlinkUpdate(@NotNull HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }

        String description = e.getDescription();
        if (StringUtil.isEmpty(description) || !description.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
          return;
        }

        String elementName = e.getDescription().substring(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL.length());

        final PsiElement targetElement = myProvider.getDocumentationElementForLink(PsiManager.getInstance(myProject), elementName, myContext);
        if (targetElement != null) {
          LightweightHint hint = myHint;
          if (hint != null) {
            hint.hide(true);
          }
          myDocumentationManager.showJavaDocInfo(targetElement, myContext, null);
        }
      }
    }
  }
}


