package pl.domradar.ingestion.adapter.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimalny parser robots.txt: reguły Disallow/Allow dla naszego User-Agenta lub "*".
 * Wybierana jest najdłuższa pasująca reguła (zgodnie z RFC 9309).
 */
public final class RobotsTxtPolicy {

    private record Rule(String prefix, boolean allow) {
    }

    private final List<Rule> rules;

    private RobotsTxtPolicy(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsTxtPolicy allowAll() {
        return new RobotsTxtPolicy(List.of());
    }

    public static RobotsTxtPolicy disallowAll() {
        return new RobotsTxtPolicy(List.of(new Rule("/", false)));
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
            if (key.equals("disallow") && value.isEmpty()) {
                continue;
            }
            Rule rule = new Rule(value, key.equals("allow"));
            for (String agent : currentAgents) {
                if (agent.equals("*")) {
                    wildcard.add(rule);
                } else if (agentToken.contains(agent) || agent.contains(agentToken)) {
                    specific.add(rule);
                }
            }
        }
        return new RobotsTxtPolicy(specific.isEmpty() ? wildcard : specific);
    }

    public boolean isAllowed(String path) {
        Rule best = null;
        for (Rule rule : rules) {
            if (path.startsWith(rule.prefix()) && (best == null || rule.prefix().length() > best.prefix().length()
                    || (rule.prefix().length() == best.prefix().length() && rule.allow()))) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }
}
