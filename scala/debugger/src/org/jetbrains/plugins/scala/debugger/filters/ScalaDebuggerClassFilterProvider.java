package org.jetbrains.plugins.scala.debugger.filters;

import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScalaDebuggerClassFilterProvider implements DebuggerClassFilterProvider {

    private static final String[] PROHIBITED_CLASS_PATTERNS = {"scala.*"};

    private static final ClassFilter[] FILTERS =
            Arrays.stream(PROHIBITED_CLASS_PATTERNS).map(ClassFilter::new).toArray(ClassFilter[]::new);

    @Override
    public List<ClassFilter> getFilters() {
        final var settings = ScalaDebuggerSettings.getInstance();
        final var flag = settings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS;
        final var list = new ArrayList<ClassFilter>();
        if (flag) {
            list.addAll(Arrays.asList(FILTERS));
            return list;
        }
        return list;
    }
}