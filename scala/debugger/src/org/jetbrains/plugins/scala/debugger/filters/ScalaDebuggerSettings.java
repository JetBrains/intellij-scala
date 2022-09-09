package org.jetbrains.plugins.scala.debugger.filters;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.debugger.DebuggerBundle;

import java.util.Collection;
import java.util.Collections;

import static java.util.Collections.singletonList;

public class ScalaDebuggerSettings extends XDebuggerSettings<ScalaDebuggerSettings> implements Getter<ScalaDebuggerSettings> {

  public boolean DEBUG_DISABLE_SPECIFIC_SCALA_METHODS = true;
  public boolean FRIENDLY_COLLECTION_DISPLAY_ENABLED = true;
  public boolean DONT_SHOW_RUNTIME_REFS = true;
  public boolean SHOW_VARIABLES_FROM_OUTER_SCOPES = true;
  public boolean ALWAYS_SMART_STEP_INTO = true;

  public ScalaDebuggerSettings() {
    super("scala_debugger");
  }

  @NotNull
  @Override
  public Collection<? extends Configurable> createConfigurables(@NotNull DebuggerSettingsCategory category) {
    //todo: split settings configurables somehow
    switch (category) {
      case GENERAL:
        return singletonList(new ScalaDebuggerSettingsConfigurable(this));
      case STEPPING:
        final var displayName = DebuggerBundle.message("scala.debug.caption");
        return singletonList(SimpleConfigurable.create("scala.debugger.stepping", displayName, ScalaSteppingConfigurable.class, this));
      default:
        return Collections.emptyList();
    }
  }

  @Override
  public ScalaDebuggerSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull final ScalaDebuggerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static ScalaDebuggerSettings getInstance() {
    return getInstance(ScalaDebuggerSettings.class);
  }

  @Override
  public ScalaDebuggerSettings get() {
    return this;
  }
}