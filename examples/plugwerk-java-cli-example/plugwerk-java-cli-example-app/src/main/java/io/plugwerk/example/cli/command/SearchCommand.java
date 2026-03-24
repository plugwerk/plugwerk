package io.plugwerk.example.cli.command;

import io.plugwerk.example.cli.PlugwerkCli;
import io.plugwerk.spi.model.PluginInfo;
import io.plugwerk.spi.model.SearchCriteria;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.List;

/**
 * Searches the catalog using one or more filter criteria.
 *
 * <p>Usage:
 * <pre>
 *   plugwerk-cli search analytics
 *   plugwerk-cli search --category=reporting
 *   plugwerk-cli search --tag=experimental --compatible-with=2.0.0
 *   plugwerk-cli search "data tool" --category=analytics
 * </pre>
 */
@Command(
        name = "search",
        description = "Search for plugins by keyword, category, tag, or system version compatibility.",
        mixinStandardHelpOptions = true)
public class SearchCommand implements Runnable {

    @ParentCommand
    private PlugwerkCli parent;

    @Parameters(index = "0", arity = "0..1", description = "Free-text search query (plugin ID, name, description, tags)")
    private String query;

    @Option(names = {"--category", "-c"}, description = "Filter by category (exact match)")
    private String category;

    @Option(names = {"--tag", "-t"}, description = "Filter by tag (exact match)")
    private String tag;

    @Option(names = {"--compatible-with"}, description = "Only show plugins compatible with this system version (e.g. 2.0.0)")
    private String compatibleWith;

    @Override
    public void run() {
        SearchCriteria criteria = new SearchCriteria(query, category, tag, compatibleWith);
        List<PluginInfo> results = parent.getMarketplace().catalog().searchPlugins(criteria);

        if (results.isEmpty()) {
            System.out.println("No plugins matched your search criteria.");
            return;
        }

        String fmt = "%-40s %-12s %-20s %s%n";
        System.out.printf(fmt, "PLUGIN ID", "VERSION", "CATEGORIES", "NAME");
        System.out.println("-".repeat(95));
        for (PluginInfo p : results) {
            System.out.printf(fmt,
                    truncate(p.getPluginId(), 40),
                    orDash(p.getLatestVersion()),
                    truncate(String.join(", ", p.getCategories()), 20),
                    truncate(p.getName(), 25));
        }
        System.out.println();
        System.out.printf("%d result(s).%n", results.size());
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "-";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String orDash(String s) {
        return s != null ? s : "-";
    }
}
