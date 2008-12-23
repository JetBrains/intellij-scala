package org.jetbrains.plugins.scala.debugger.filters;

import com.intellij.ui.classFilter.DebuggerClassFilterProvider;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author ilyas
 */
public class ScalaDebuggerClassFilterProvider implements DebuggerClassFilterProvider {

  @NonNls
  private static final String[] PROHIBITED_CLASS_PATTERNS =
    {"scala.runtime.*"};

  private static ClassFilter[] FITERS = ContainerUtil.map(PROHIBITED_CLASS_PATTERNS, new Function<String, ClassFilter>() {
    public ClassFilter fun(final String s) {
      return new ClassFilter(s);
    }
  }, new ClassFilter[0]);

  public List<ClassFilter> getFilters() {

    final ScalaDebuggerSettings settings = ScalaDebuggerSettings.getInstance();
    final Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS;
    final ArrayList<ClassFilter> list = new ArrayList<ClassFilter>();
    if (flag == null || flag.booleanValue()) {
      list.addAll(Arrays.asList(FITERS));
      return list;
    }
    return list;
  }

}