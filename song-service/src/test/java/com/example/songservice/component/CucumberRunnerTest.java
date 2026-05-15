package com.example.songservice.component;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@ConfigurationParameter(key = "cucumber.features", value = "classpath:features/song/song_management.feature")
@ConfigurationParameter(key = "cucumber.glue", value = "com.example.songservice.component")
@ConfigurationParameter(
        key = "cucumber.plugin",
        value = "pretty, html:target/cucumber-reports/song-service-component.html")
public class CucumberRunnerTest {
}
