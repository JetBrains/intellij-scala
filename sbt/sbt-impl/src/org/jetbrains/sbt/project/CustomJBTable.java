package org.jetbrains.sbt.project;

import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.ModuleHeuristicResult;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Only used in {@link MigrateConfigurationsDialogWrapper}.
 * <p>
 * This class <b><i>must</i></b> be written in Java. Otherwise, the Scala 3 compiler
 * emits some synthetic forwarder methods to a particular package-private
 * method in {@link javax.swing.JTable} {@code dropLocationForPoint(Point)} which
 * the IntelliJ IDEA Plugin API check does not like.
 *
 * @see <a href="https://youtrack.jetbrains.com/issue/SCL-24882">SCL-24882</a>
 */
final class CustomJBTable extends JBTable {
    private final DefaultTableModel tableModel;
    private final Map<ModuleBasedConfiguration<? extends RunConfigurationModule, ?>, ModuleHeuristicResult> configurationToModule;
    private final Map<ModuleBasedConfiguration<? extends RunConfigurationModule, ?>, DefaultCellEditor> configToComboBoxCellEditor;

    public CustomJBTable(
            DefaultTableModel tableModel,
            Map<ModuleBasedConfiguration<? extends RunConfigurationModule, ?>, ModuleHeuristicResult> configurationToModule,
            Map<ModuleBasedConfiguration<? extends RunConfigurationModule, ?>, DefaultCellEditor> configToComboBoxCellEditor
    ) {
        this.tableModel = tableModel;
        this.configurationToModule = configurationToModule;
        this.configToComboBoxCellEditor = configToComboBoxCellEditor;
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
        if (column == 2) return getCellEditorForModulesComboBoxColumn(row, column);
        return super.getCellEditor(row, column);
    }

    @Override
    public String getToolTipText(@NotNull MouseEvent event) {
        // TODO The tooltip doesn't work when the user just clicks on it.
        //      In order for a tooltip to appear, the user has to move the mouse a little after clicking on it.
        final var row = rowAtPoint(event.getPoint());
        final var config = findConfigInRow(row);
        List<String> guesses;
        if (config == null) {
            guesses = Collections.emptyList();
        } else {
            final var module = configurationToModule.get(config);
            if (module == null) {
                guesses = Collections.emptyList();
            } else {
                final var iterator = module.guesses().iterator();
                guesses = new ArrayList<>();
                while (iterator.hasNext()) {
                    guesses.add(iterator.next());
                }
            }
        }
        if (guesses.isEmpty()) return super.getToolTipText(event);
        return "Suggested modules: " + Strings.join(guesses, ", ");
    }

    private TableCellEditor getCellEditorForModulesComboBoxColumn(int row, int column) {
        final var config = findConfigInRow(row);
        if (config == null) return super.getCellEditor(row, column);

        final var editor = configToComboBoxCellEditor.get(config);
        if (editor != null) return editor;

        return super.getCellEditor(row, column);
    }

    @Nullable
    private ModuleBasedConfiguration<? extends RunConfigurationModule, ?> findConfigInRow(int row) {
        final var isRowWithinRange = row >= 0 && row < configurationToModule.size();
        if (!isRowWithinRange) return null;
        return (ModuleBasedConfiguration<? extends RunConfigurationModule, ?>) tableModel.getValueAt(row, 0);
    }
}
