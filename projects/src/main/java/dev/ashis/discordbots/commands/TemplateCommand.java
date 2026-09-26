package dev.ashis.discordbots.commands;

public record TemplateCommand(Template template, String group, String name) {
    public String id() {
        return template.key() + "/" + group + "/" + name;
    }

    public String description() {
        String label = name.replace('-', ' ');
        return (Character.toUpperCase(label.charAt(0)) + label.substring(1) + " command").substring(0, Math.min(100, label.length() + 8));
    }
}
