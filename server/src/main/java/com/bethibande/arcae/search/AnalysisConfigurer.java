package com.bethibande.arcae.search;

import io.quarkus.hibernate.search.orm.elasticsearch.SearchExtension;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;

@SearchExtension
public class AnalysisConfigurer implements ElasticsearchAnalysisConfigurer {

    @Override
    public void configure(final ElasticsearchAnalysisConfigurationContext ctx) {
        ctx.analyzer("artifact_path")
                .custom()
                .tokenizer("artifact_path_tokenizer");
        ctx.tokenizer("artifact_path_tokenizer")
                .type("char_group")
                .param("tokenize_on_chars", "whitespace", "punctuation", ".", "-", ":");

    }
}
