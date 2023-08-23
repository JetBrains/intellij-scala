package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public final class SimpleMappingListCellRenderer<T extends Enum<T>> extends SimpleListCellRenderer<T> {
    private final Map<T, String> mapping;

    public SimpleMappingListCellRenderer(Map<T, String> mapping) {
        this.mapping = mapping;
    }

    @Override
    public void customize(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
        @Nls String displayText = value == null ? null : mapping.getOrDefault(value, "");
        setText(displayText);
    }

    @SafeVarargs
    public static <T extends Enum<T>> SimpleMappingListCellRenderer<T> create(Pair<T, String>... pairs) {
        HashMap<T, String> mapping = new HashMap<>();
        for (Pair<T, String> pair : pairs) {
            mapping.put(pair.first, pair.second);
        }
        return new SimpleMappingListCellRenderer<>(mapping);
    }

    @SafeVarargs
    public static <T extends Enum<T>> SimpleMappingListCellRenderer<T> create(scala.Tuple2<T, String>... pairs) {
        HashMap<T, String> mapping = new HashMap<>();
        for (scala.Tuple2<T, String> pair : pairs) {
            mapping.put(pair._1, pair._2);
        }
        return new SimpleMappingListCellRenderer<>(mapping);
    }
}
