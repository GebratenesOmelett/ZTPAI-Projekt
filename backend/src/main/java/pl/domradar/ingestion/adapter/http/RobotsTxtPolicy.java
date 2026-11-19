package pl.domradar.ingestion.adapter.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parser robots.txt zgodny z RFC 9309: grupy {@code User-agent}, reguły {@code Allow}/{@code Disallow}
 * ze wzorcami {@code *} (dowolny ciąg) i {@code $} (koniec ścieżki). Reguły dopasowujemy do ścieżki
 * razem z query stringiem; wygrywa najdłuższa pasująca reguła, a przy remisie — {@code Allow}.
 * Jeśli istnieje grupa dla naszego User-Agenta, używamy tylko jej; w przeciwnym razie grupy {@code *}.
 */
public final class RobotsTxtPolicy {

    private record Rule(String pattern, boolean allow, Pattern regex) {

        static Rule of(String pattern, boolean allow) {
            StringBuilder re = new StringBuilder("^");
            boolean anchored = pattern.endsWith("$");
            String body = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
            for (String part : body.split("\\*", -1)) {
                if (re.length() > 1) {
                    re.append(".*");
                }
                re.append(Pattern.quote(part));
            }
            if (anchored) {
                re.append('$');
            }
            return new Rule(pattern, allow, Pattern.compile(re.toString()));
        }

        boolean matches(String path) {
            return regex.matcher(path).find();
        }
    }

    private final List<Rule> rules;

    private RobotsTxtPolicy(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsTxtPolicy allowAll() {
        return new RobotsTxtPolicy(List.of());
    }

    public static RobotsTxtPolicy disallowAll() {
        return new RobotsTxtPolicy(List.of(Rule.of("/", false)));
    }

    public static RobotsTxtPolicy parse(String robotsTxt, String userAgent) {
        String agentToken = userAgent.split("[/ ]")[0].toLowerCase(Locale.ROOT);
        List<Rule> specific = new ArrayList<>();
        List<Rule> wildcard = new ArrayList<>();
        List<String> currentAgents = new ArrayList<>();
        boolean lastWasAgent = false;
        for (String rawLine : robotsTxt.split("\\R")) {
            String line = rawLine.replaceAll("#.*$", "").trim();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (!lastWasAgent) {
                    currentAgents.clear();
                }
                currentAgents.add(value.toLowerCase(Locale.ROOT));
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (!key.equals("allow") && !key.equals("disallow")) {
                continue;
            }
            if (value.isEmpty()) {
                continue;
            }
            Rule rule = Rule.of(value, key.equals("allow"));
            for (String agent : currentAgents) {
                if (agent.equals("*")) {
                    wildcard.add(rule);
                } else if (agentToken.equals(agent) || agentToken.startsWith(agent)) {
                    specific.add(rule);
                }
            }
        }
        return new RobotsTxtPolicy(specific.isEmpty() ? wildcard : specific);
    }

    /** @param pathAndQuery ścieżka URL razem z query stringiem, np. {@code /pl/wyniki?page=2} */
    public boolean isAllowed(String pathAndQuery) {
        String path = pathAndQuery == null || pathAndQuery.isEmpty() ? "/" : pathAndQuery;
        Rule best = null;
        for (Rule rule : rules) {
            if (!rule.matches(path)) {
                continue;
            }
            if (best == null || rule.pattern().length() > best.pattern().length()
                    || (rule.pattern().length() == best.pattern().length() && rule.allow())) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }
}
