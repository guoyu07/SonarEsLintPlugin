package org.sonar.plugin.eslint.api;

import org.sonar.plugin.eslint.EsLintExecutorConfig;
import org.sonar.api.batch.BatchSide;

import java.util.List;

@BatchSide
public interface EsLintExecutor {
    List<String> execute(EsLintExecutorConfig config, List<String> files);
}
