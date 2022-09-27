// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.scala.testDiscovery;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import com.intellij.util.io.PowerStatus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember;
import org.jetbrains.plugins.scala.testDiscovery.actions.ScalaShowAffectedTestsAction;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.intellij.ui.SimpleTextAttributes.STYLE_UNDERLINE;

final public class ScalaAffectedTestsInChangeListPainter implements ChangeListDecorator, Disposable {
  private final Project myProject;
  private final Alarm myAlarm;
  private final AtomicReference<Set<String>> myChangeListsToShow = new AtomicReference<>(Collections.emptySet());

  private static final String SHOW_AFFECTED_TESTS_IN_CHANGELISTS = "show.affected.tests.in.changelists";

  public ScalaAffectedTestsInChangeListPainter(@NotNull Project project) {
    myProject = project;
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

    ChangeListListener changeListListener = new ChangeListAdapter() {
      @Override
      public void changeListsChanged() {
        scheduleUpdate();
      }

      @Override
      public void changeListUpdateDone() {
        scheduleUpdate();
      }

      @Override
      public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList, boolean automatic) {
        scheduleUpdate();
      }

      @Override
      public void unchangedFileStatusChanged() {
        scheduleUpdate();
      }
    };

    MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(ChangeListListener.TOPIC, changeListListener);
    StartupManager.getInstance(myProject)
            .runAfterOpened(() -> DumbService.getInstance(myProject).runWhenSmart(this::scheduleUpdate));
  }

  @Override
  public void dispose() {
    myChangeListsToShow.set(Collections.emptySet());
  }

  private void scheduleRefresh() {
    if (!myProject.isDisposed()) {
      ChangesViewManager.getInstance(myProject).scheduleRefresh();
    }
  }

  private static int updateDelay() {
    return PowerStatus.getPowerStatus() == PowerStatus.AC ? 50 : 300;
  }

  @Override
  public void decorateChangeList(LocalChangeList changeList,
                                 ColoredTreeCellRenderer renderer,
                                 boolean selected,
                                 boolean expanded,
                                 boolean hasFocus) {
    if (!Registry.is(SHOW_AFFECTED_TESTS_IN_CHANGELISTS)) return;
    if (!ScalaShowAffectedTestsAction.isEnabled(myProject)) return;
    if (changeList.getChanges().isEmpty()) return;
    if (!myChangeListsToShow.get().contains(changeList.getId())) return;

    renderer.append(", ", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    renderer.append(ScalaBundle.message("show.affected.tests.scala"), new SimpleTextAttributes(STYLE_UNDERLINE, UIUtil.getInactiveTextColor()), (Runnable) () -> {
      DataContext dataContext = DataManager.getInstance().getDataContext(renderer.getTree());
      Change[] changes = changeList.getChanges().toArray(new Change[0]);
      ScalaShowAffectedTestsAction.showDiscoveredTestsByChanges(myProject, changes, changeList.getName(), dataContext);
    });
  }

  private void scheduleUpdate() {
    if (!Registry.is(SHOW_AFFECTED_TESTS_IN_CHANGELISTS)) return;
    if (!ScalaShowAffectedTestsAction.isEnabled(myProject)) return;
    myAlarm.cancelAllRequests();
    if (!myAlarm.isDisposed()) {
      myAlarm.addRequest(this::update, updateDelay());
    }
  }

  private void update() {
    List<LocalChangeList> changeLists = ChangeListManager.getInstance(myProject).getChangeLists();
    Set<String> result = changeLists.stream()
      .filter(list -> !list.getChanges().isEmpty())
      .map((LocalChangeList list) -> {
        Collection<Change> changes = list.getChanges();

        ScMember[] methods = ScalaShowAffectedTestsAction.findMembers(myProject, changes.toArray(new Change[0]));
        List<String> paths = ScalaShowAffectedTestsAction.getRelativeAffectedPaths(myProject, changes);
        if (methods.length == 0 && paths.isEmpty()) return null;

        Ref<String> ref = Ref.create();
        ScalaShowAffectedTestsAction.processMembers(myProject, methods, paths, (clazz, method, parameter) -> {
          ref.set(list.getId());
          return false;
        });
        return ref.get();
      }).filter(Objects::nonNull).collect(Collectors.toSet());
    myChangeListsToShow.set(result);

    EdtInvocationManager.getInstance().invokeLater(this::scheduleRefresh);
  }
}