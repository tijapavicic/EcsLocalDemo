package com.example.core.domain;

import java.util.List;

/**
 * Generic configuration container for validation/processing rules.
 *
 * <p><b>Generics Benefit:</b> Single config class handles any list of rules
 * (extensions, MIME types, patterns, constraints, etc.)
 *
 * <p><b>Type Parameter:</b>
 * <ul>
 *   <li>R = the rule type (String for extensions, Pattern for regex, Constraint for validation)</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>
 * // File extensions
 * GenericRuleConfiguration&lt;String&gt; extensionRules =
 *   new GenericRuleConfiguration&lt;&gt;(List.of("pdf", "docx", "xlsx"));
 *
 * // MIME type patterns
 * GenericRuleConfiguration&lt;String&gt; mimeRules =
 *   new GenericRuleConfiguration&lt;&gt;(List.of("image/*", "application/pdf"));
 *
 * // Numeric constraints
 * GenericRuleConfiguration&lt;Long&gt; sizeRules =
 *   new GenericRuleConfiguration&lt;&gt;(List.of(10485760L));  // 10MB limit
 * </pre>
 *
 * @param <R> the rule type
 * @since 3.0
 */
public class GenericRuleConfiguration<R> {

    private final List<R> rules;
    private final boolean enforcementEnabled;

    /**
     * Create configuration with rules.
     *
     * @param rules the list of rules (empty list = no enforcement)
     */
    public GenericRuleConfiguration(List<R> rules) {
        this.rules = rules != null ? rules : List.of();
        this.enforcementEnabled = !this.rules.isEmpty();
    }

    /**
     * Check if enforcement is enabled (non-empty rule list).
     *
     * @return true if rules exist, false if empty
     */
    public boolean isEnforced() {
        return enforcementEnabled;
    }

    /**
     * Get all rules.
     *
     * @return immutable list of rules
     */
    public List<R> getRules() {
        return List.copyOf(rules);
    }

    /**
     * Check if a rule is in the list.
     *
     * @param rule the rule to check
     * @return true if rule is in the list
     */
    public boolean contains(R rule) {
        return rules.contains(rule);
    }

    /**
     * Get the count of rules.
     *
     * @return number of rules
     */
    public int size() {
        return rules.size();
    }

    @Override
    public String toString() {
        return "GenericRuleConfiguration{" +
                "rules=" + rules +
                ", enforcementEnabled=" + enforcementEnabled +
                '}';
    }
}

