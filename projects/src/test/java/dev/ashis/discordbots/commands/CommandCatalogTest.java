package dev.ashis.discordbots.commands;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandCatalogTest {
    private static final Pattern DISCORD_NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    @Test
    void everyTemplateHasAtLeast75DiscordCompatibleCommands() {
        for (Template template : Template.values()) {
            assertTrue(template.allCommands().size() >= 75, template.key() + " must have at least 75 commands");
            assertEquals(3, template.groupNames().size(), template.key() + " should use three subcommand groups");
            for (String group : template.groupNames()) {
                assertTrue(template.commandsInGroup(group).size() <= 25,
                        template.key() + "/" + group + " exceeds Discord's subcommand group limit");
            }
        }
    }

    @Test
    void commandsHaveUniqueNamesWithinTemplatesAndResolvableIds() {
        for (Template template : Template.values()) {
            Set<String> names = new HashSet<>(template.allCommands());
            assertEquals(template.allCommands().size(), names.size(), template.key() + " contains duplicate command names");

            for (String name : names) {
                assertTrue(DISCORD_NAME.matcher(name).matches(), "Invalid Discord command name: " + name);
                TemplateCommand command = CommandCatalog.find(template, name);
                assertNotNull(command, "Command should be present in the shared catalog: " + name);
                assertTrue(command.description().length() <= 100);
            }
        }
    }
}
