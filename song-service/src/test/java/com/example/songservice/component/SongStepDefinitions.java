package com.example.songservice.component;

import com.example.songservice.dto.SongRequest;
import com.example.songservice.repository.SongRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SongStepDefinitions {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SongRepository songRepository;

    private ResultActions result;
    private SongRequest currentRequest;

    @Before
    public void cleanDatabase() {
        songRepository.deleteAll();
    }

    @Given("a valid song request with id {long}, name {string}, artist {string}")
    public void aValidSongRequest(long id, String name, String artist) {
        currentRequest = new SongRequest();
        currentRequest.setId(id);
        currentRequest.setName(name);
        currentRequest.setArtist(artist);
        currentRequest.setAlbum("Test Album");
        currentRequest.setDuration("03:30");
        currentRequest.setYear("2000");
    }

    @When("the client POSTs the song to {string}")
    public void theClientPostsTheSong(String path) throws Exception {
        result = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(currentRequest)));
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int httpStatus) throws Exception {
        result.andExpect(status().is(httpStatus));
    }

    @And("the response body contains id {long}")
    public void theResponseBodyContainsId(long id) throws Exception {
        result.andExpect(jsonPath("$.id").value(id));
    }

    @Given("a song with id {long} already exists")
    public void aSongWithIdAlreadyExists(long id) throws Exception {
        SongRequest req = new SongRequest();
        req.setId(id);
        req.setName("Existing Song");
        req.setArtist("Existing Artist");
        req.setAlbum("Existing Album");
        req.setDuration("02:00");
        req.setYear("2001");
        mockMvc.perform(post("/songs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));
    }

    @And("the response body errorMessage contains {string}")
    public void theResponseBodyErrorMessageContains(String text) throws Exception {
        result.andExpect(jsonPath("$.errorMessage").value(containsString(text)));
    }

    @When("the client GETs {string}")
    public void theClientGets(String path) throws Exception {
        result = mockMvc.perform(get(path));
    }

    @When("the client DELETEs {string} with param id {string}")
    public void theClientDeletesWithParamId(String path, String idParam) throws Exception {
        result = mockMvc.perform(delete(path).param("id", idParam));
    }

    @Then("the response ids list is empty")
    public void theResponseIdsListIsEmpty() throws Exception {
        result.andExpect(jsonPath("$.ids").value(empty()));
    }
}
