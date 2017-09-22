package org.sonar.plugin.eslint.api;

import org.sonar.plugin.eslint.model.EsLintIssue;

import java.util.List;
import java.util.Map;

public interface EsLintParser {
    Map<String, List<EsLintIssue>> parse(List<String> rawOutputBatches);
}
