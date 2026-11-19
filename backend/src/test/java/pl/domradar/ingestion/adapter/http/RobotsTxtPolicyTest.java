package pl.domradar.ingestion.adapter.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtPolicyTest {

    private static final String UA = "DomRadarBot/1.0 (+https://example.com)";

    @Test
    void wildcardRulesWithLongestMatchWinning() {
        RobotsTxtPolicy policy = RobotsTxtPolicy.parse("""
                User-agent: *
                Disallow: /pl/oferta/
                Allow: /pl/oferta/publiczne/
                Disallow: /api
                """, UA);

        assertThat(policy.isAllowed("/pl/wyniki/sprzedaz")).isTrue();
        assertThat(policy.isAllowed("/pl/oferta/123")).isFalse();
        assertThat(policy.isAllowed("/pl/oferta/publiczne/1")).isTrue();
        assertThat(policy.isAllowed("/api/v1")).isFalse();
    }

    @Test
    void agentSpecificGroupOverridesWildcard() {
        RobotsTxtPolicy policy = RobotsTxtPolicy.parse("""
                User-agent: *
                Allow: /

                User-agent: DomRadarBot
                Disallow: /
                """, UA);

        assertThat(policy.isAllowed("/anything")).isFalse();
    }

    @Test
    void emptyDisallowAllowsEverything() {
        assertThat(RobotsTxtPolicy.parse("User-agent: *\nDisallow:", UA).isAllowed("/x")).isTrue();
        assertThat(RobotsTxtPolicy.disallowAll().isAllowed("/")).isFalse();
    }

    @Test
    void wildcardsAndEndAnchorFollowRfc9309() {
        RobotsTxtPolicy policy = RobotsTxtPolicy.parse("""
                User-agent: *
                Disallow: */ajax/
                Disallow: /api/
                Allow: /api/v1/offers/
                Disallow: /*sort=*
                Disallow: /*.pdf$
                """, UA);

        assertThat(policy.isAllowed("/pl/ajax/contact")).isFalse();
        assertThat(policy.isAllowed("/api/v1/offers/?offset=0&category_id=14")).isTrue();
        assertThat(policy.isAllowed("/api/v1/users/1")).isFalse();
        assertThat(policy.isAllowed("/mieszkania/warszawa?sort=price")).isFalse();
        assertThat(policy.isAllowed("/mieszkania/warszawa?page=2")).isTrue();
        assertThat(policy.isAllowed("/files/plan.pdf")).isFalse();
        assertThat(policy.isAllowed("/files/plan.pdf?x=1")).isTrue();
    }
}
