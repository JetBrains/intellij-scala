package intellijhocon.settings;

import com.intellij.openapi.project.Project;

import javax.swing.*;

public class HoconProjectSettingsPanel {
    private final Project project;

    private JPanel mainPanel;
    private JCheckBox classReferencesUnquotedCheckBox;
    private JCheckBox classReferencesQuotedCheckBox;

    public HoconProjectSettingsPanel(Project project) {
        this.project = project;
        loadSettings();
    }

    public void loadSettings() {
        HoconProjectSettings settings = HoconProjectSettings.getInstance(project);
        classReferencesUnquotedCheckBox.setSelected(settings.getClassReferencesOnUnquotedStrings());
        classReferencesQuotedCheckBox.setSelected(settings.getClassReferencesOnQuotedStrings());
    }

    public JComponent getMainComponent() {
        return mainPanel;
    }

    public boolean isModified() {
        HoconProjectSettings settings = HoconProjectSettings.getInstance(project);
        return classReferencesUnquotedCheckBox.isSelected() != settings.getClassReferencesOnUnquotedStrings() ||
            classReferencesQuotedCheckBox.isSelected() != settings.getClassReferencesOnQuotedStrings();
    }

    public void apply() {
        HoconProjectSettings settings = HoconProjectSettings.getInstance(project);
        settings.setClassReferencesOnUnquotedStrings(classReferencesUnquotedCheckBox.isSelected());
        settings.setClassReferencesOnQuotedStrings(classReferencesQuotedCheckBox.isSelected());
    }
}
