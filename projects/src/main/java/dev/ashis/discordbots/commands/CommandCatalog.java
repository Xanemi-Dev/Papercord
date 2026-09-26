package dev.ashis.discordbots.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommandCatalog {
    private static final Map<String, TemplateCommand> COMMANDS = createCommands();

    private CommandCatalog() {
    }

    public static List<TemplateCommand> all() {
        return List.copyOf(COMMANDS.values());
    }

    public static TemplateCommand find(Template template, String name) {
        if (template == null || name == null) {
            return null;
        }
        return COMMANDS.get(template.key() + "/" + name);
    }

    private static Map<String, TemplateCommand> createCommands() {
        Map<String, TemplateCommand> commands = new LinkedHashMap<>();
        for (Template template : Template.values()) {
            for (String group : template.groupNames()) {
                for (String name : template.commandsInGroup(group)) {
                    TemplateCommand command = new TemplateCommand(template, group, name);
                    commands.put(template.key() + "/" + name, command);
                }
            }
        }
        return commands;
    }
}
