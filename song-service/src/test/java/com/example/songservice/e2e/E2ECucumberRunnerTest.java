package com.example.songservice.e2e;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@ConfigurationParameter(key = "cucumber.features", value = "classpath:features/e2e/song_api_e2e.feature")
@ConfigurationParameter(key = "cucumber.glue", value = "com.example.songservice.e2e")
@ConfigurationParameter(
        key = "cucumber.plugin",
        value = "pretty, html:target/cucumber-reports/song-service-e2e.html")
public class E2ECucumberRunnerTest {
}
