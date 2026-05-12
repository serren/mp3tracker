package com.example.songservice.e2e;

import com.example.songservice.dto.SongRequest;
import com.example.songservice.repository.SongRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SongApiE2ESteps {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    SongRepository songRepository;

    @Autowired
    ObjectMapper objectMapper;

    private ResponseEntity<String> lastResponse;
    private SongRequest currentRequest;

    @Before
    public void cleanDatabase() {
        songRepository.deleteAll();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Given("a song request with id {long}, name {string}, artist {string}")
    public void aSongRequest(long id, String name, String artist) {
        currentRequest = new SongRequest();
        currentRequest.setId(id);
        currentRequest.setName(name);
        currentRequest.setArtist(artist);
        currentRequest.setAlbum("E2E Album");
        currentRequest.setDuration("04:00");
        currentRequest.setYear("2005");
    }

    @When("the song is created via POST {string}")
    public void theSongIsCreatedViaPost(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SongRequest> entity = new HttpEntity<>(currentRequest, headers);
        lastResponse = restTemplate.postForEntity(baseUrl() + path, entity, String.class);
    }

    @Then("the HTTP status is {int}")
    public void theHttpStatusIs(int statusCode) {
        assertThat(lastResponse.getStatusCode().value()).isEqualTo(statusCode);
    }

    @And("the response contains id {long}")
    public void theResponseContainsId(long id) throws Exception {
        Map<?, ?> body = objectMapper.readValue(lastResponse.getBody(), Map.class);
        assertThat(((Number) body.get("id")).longValue()).isEqualTo(id);
    }

    @When("the song is fetched via GET {string}")
    public void theSongIsFetchedViaGet(String path) {
        lastResponse = restTemplate.getForEntity(baseUrl() + path, String.class);
    }

    @And("the response contains name {string}")
    public void theResponseContainsName(String name) throws Exception {
        Map<?, ?> body = objectMapper.readValue(lastResponse.getBody(), Map.class);
        assertThat(body.get("name")).isEqualTo(name);
    }

    @When("the song is deleted via DELETE {string}")
    public void theSongIsDeletedViaDelete(String path) {
        lastResponse = restTemplate.exchange(baseUrl() + path, HttpMethod.DELETE, null, String.class);
    }

    @And("a subsequent GET {string} returns 404")
    public void aSubsequentGetReturns404(String path) {
        ResponseEntity<String> getResponse = restTemplate.getForEntity(baseUrl() + path, String.class);
        assertThat(getResponse.getStatusCode().value()).isEqualTo(404);
    }

    @Given("a song request with id {long} and invalid year {string}")
    public void aSongRequestWithInvalidYear(long id, String year) {
        currentRequest = new SongRequest();
        currentRequest.setId(id);
        currentRequest.setName("Invalid Year Song");
        currentRequest.setArtist("Artist");
        currentRequest.setAlbum("Album");
        currentRequest.setDuration("02:00");
        currentRequest.setYear(year);
    }

    @And("the response body contains errorMessage with {string}")
    public void theResponseBodyContainsErrorMessageWith(String text) throws Exception {
        Map<?, ?> body = objectMapper.readValue(lastResponse.getBody(), Map.class);
        assertThat(body.get("errorMessage").toString()).contains(text);
    }
}
