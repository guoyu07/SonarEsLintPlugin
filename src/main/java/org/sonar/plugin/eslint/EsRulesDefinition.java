package org.sonar.plugin.eslint;

import org.sonar.plugin.eslint.model.EsLintRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.Settings;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.debt.DebtRemediationFunction.Type;
import org.sonar.api.server.rule.RulesDefinition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class EsRulesDefinition implements RulesDefinition {
    public static final String REPOSITORY_NAME = "eslint";
    public static final String DEFAULT_RULE_SEVERITY = Severity.defaultSeverity();
    public static final String DEFAULT_RULE_DESCRIPTION = "No description provided for this ESLint rule";
    public static final String DEFAULT_RULE_DEBT_SCALAR = "0min";
    public static final String DEFAULT_RULE_DEBT_OFFSET = "0min";
    public static final String DEFAULT_RULE_DEBT_TYPE = RuleType.CODE_SMELL.name();
    /**
     * The SonarQube rule that will contain all unknown ESLint issues.
     */
    public static final EsLintRule ESLINT_UNKNOWN_RULE = new EsLintRule(
            "eslint-issue",
            Severity.MAJOR,
            "EsLint issues that are not yet known to the plugin",
            "No description for ESLint rule",
            "");
    private static final Logger LOG = LoggerFactory.getLogger(EsRulesDefinition.class);
    @SuppressWarnings("HardcodedFileSeparator")
    private static final String CORE_RULES_CONFIG_RESOURCE_PATH = "/eslint/eslint-rules.properties";
    private static final String DEFAULT_TAGS = "eslint" ;

    private final Settings settings;
    private final List<EsLintRule> eslintCoreRules = new ArrayList<>(100);
    private final List<EsLintRule> eslintRules = new ArrayList<>(100);

    public EsRulesDefinition() {
        this(null);
    }

    public EsRulesDefinition(Settings settings) {

        this.settings = settings;

        loadCoreRules();
        loadCustomRules();
    }

    public static void loadRules(InputStream stream, List<EsLintRule> rulesCollection) {
        Properties properties = new Properties();

        try {
            properties.load(stream);
        } catch (IOException e) {
            EsRulesDefinition.LOG.error("Error while loading ESLint rules: {}", e.getMessage(), e);
        }

        for (String propKey : properties.stringPropertyNames()) {

            if (propKey.contains("."))
                continue;

            String ruleEnabled = properties.getProperty(propKey);

            if (!"true".equals(ruleEnabled))
                continue;

            String ruleId = propKey;
            String tags = properties.getProperty(propKey + ".tags", EsRulesDefinition.DEFAULT_TAGS);
            String ruleName = properties.getProperty(propKey + ".name", ruleId.replace("-", " "));
            String ruleSeverity = properties.getProperty(propKey + ".severity", EsRulesDefinition.DEFAULT_RULE_SEVERITY);
            String defaultRuleDescriptionValue = EsRulesDefinition.DEFAULT_RULE_DESCRIPTION;

            if (ruleId.indexOf("/") < 0) {
                defaultRuleDescriptionValue = "See full Eslint rule description on the following link: https://eslint.org/docs/rules/" + ruleId;

            } else if (ruleId.indexOf("angular/") == 0) {
                tags += ",eslint-angular";
                defaultRuleDescriptionValue = "See full Eslint angular rule description on the following link: https://github.com/Gillespie59/eslint-plugin-angular/blob/master/docs/rules/" + ruleId.substring(8);

            } else if (ruleId.indexOf("/") >= 0) {
                tags += ",eslint-" + ruleId.substring(0, ruleId.indexOf("/"));
            }

            String ruleDescription = properties.getProperty(propKey + ".description", defaultRuleDescriptionValue);

            String debtRemediationFunction = properties.getProperty(propKey + ".debtFunc", null);
            String debtRemediationScalar = properties.getProperty(propKey + ".debtScalar", EsRulesDefinition.DEFAULT_RULE_DEBT_SCALAR);
            String debtRemediationOffset = properties.getProperty(propKey + ".debtOffset", EsRulesDefinition.DEFAULT_RULE_DEBT_OFFSET);
            String debtType = properties.getProperty(propKey + ".debtType", EsRulesDefinition.DEFAULT_RULE_DEBT_TYPE);

            EsLintRule esLintRule = null;

            // try to apply the specified debt remediation function
            if (debtRemediationFunction != null) {
                Type debtRemediationFunctionEnum = Type.valueOf(debtRemediationFunction);

                esLintRule = new EsLintRule(
                        ruleId,
                        ruleSeverity,
                        ruleName,
                        ruleDescription,
                        debtRemediationFunctionEnum,
                        debtRemediationScalar,
                        debtRemediationOffset,
                        debtType,
                        tags
                );

            }

            // no debt remediation function specified
            if (esLintRule == null) {
                esLintRule = new EsLintRule(
                        ruleId,
                        ruleSeverity,
                        ruleName,
                        ruleDescription,
                        tags
                );
            }
            esLintRule.setHtmlDescription(ruleDescription);
            rulesCollection.add(esLintRule);
        }

        rulesCollection.sort((final EsLintRule r1, final EsLintRule r2) -> r1.getKey().compareTo(r2.getKey()));
    }

    private static void createRule(RulesDefinition.NewRepository repository, EsLintRule esLintRule) {
        RulesDefinition.NewRule sonarRule =
                repository
                        .createRule(esLintRule.getKey())
                        .setName(esLintRule.getName())
                        .setSeverity(esLintRule.getSeverity())
                        .setHtmlDescription(esLintRule.getHtmlDescription())
                        .setStatus(RuleStatus.READY)
                        .setTags(esLintRule.getTagsAsArray());


        if (esLintRule.isHasDebtRemediation()) {
            DebtRemediationFunction debtRemediationFn = null;
            RulesDefinition.DebtRemediationFunctions funcs = sonarRule.debtRemediationFunctions();

            switch (esLintRule.getDebtRemediationFunction()) {
                case LINEAR:
                    debtRemediationFn = funcs.linear(esLintRule.getDebtRemediationScalar());
                    break;

                case LINEAR_OFFSET:
                    debtRemediationFn = funcs.linearWithOffset(esLintRule.getDebtRemediationScalar(), esLintRule.getDebtRemediationOffset());
                    break;

                case CONSTANT_ISSUE:
                    debtRemediationFn = funcs.constantPerIssue(esLintRule.getDebtRemediationScalar());
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown debt evaluation function " + esLintRule.getDebtRemediationFunction());
            }

            sonarRule.setDebtRemediationFunction(debtRemediationFn);
        }

        RuleType type = null;

        if (esLintRule.getDebtType() != null && RuleType.names().contains(esLintRule.getDebtType())) {
            // Try and parse it as a new-style rule type (since 5.5 SQALE's been replaced
            // with something simpler, and there's really only three buckets)
            type = RuleType.valueOf(esLintRule.getDebtType());
        }

        if (type == null) {
            type = RuleType.CODE_SMELL;
        }

        sonarRule.setType(type);
    }

    private void loadCoreRules() {
        InputStream coreRulesStream = EsRulesDefinition.class.getResourceAsStream(EsRulesDefinition.CORE_RULES_CONFIG_RESOURCE_PATH);
        EsRulesDefinition.loadRules(coreRulesStream, eslintCoreRules);
    }

    private void loadCustomRules() {
        if (settings == null)
            return;

        List<String> configKeys = settings.getKeysStartingWith(EsLintPlugin.SETTING_ES_RULE_CONFIGS);

        for (String cfgKey : configKeys) {
            if (!cfgKey.endsWith("config"))
                continue;

            String rulesConfig = settings.getString(cfgKey);
            if (rulesConfig != null) {
                InputStream rulesConfigStream = new ByteArrayInputStream(rulesConfig.getBytes(Charset.defaultCharset()));
                EsRulesDefinition.loadRules(rulesConfigStream, eslintRules);
            }
        }
    }

    @Override
    public void define(RulesDefinition.Context context) {
        RulesDefinition.NewRepository repository =
                context
                        .createRepository(EsRulesDefinition.REPOSITORY_NAME, EsLintLanguage.LANGUAGE_KEY)
                        .setName("ESLint Analyzer");

        createRule(repository, EsRulesDefinition.ESLINT_UNKNOWN_RULE);

        // add the ESLint builtin core rules
        for (EsLintRule coreRule : eslintCoreRules) {
            createRule(repository, coreRule);
        }

        // add additional custom ESLint rules
        for (EsLintRule customRule : eslintRules) {
            createRule(repository, customRule);
        }

        repository.done();
    }

    public List<EsLintRule> getCoreRules() {
        return eslintCoreRules;
    }

    public List<EsLintRule> getRules() {
        return eslintRules;
    }
}
