Feature: Song metadata management

  Scenario: Successfully create new song metadata
    Given a valid song request with id 100, name "Bohemian Rhapsody", artist "Queen"
    When the client POSTs the song to "/songs"
    Then the response status is 200
    And the response body contains id 100

  Scenario: Cannot create duplicate song metadata
    Given a song with id 200 already exists
    And a valid song request with id 200, name "Duplicate", artist "AnotherArtist"
    When the client POSTs the song to "/songs"
    Then the response status is 409
    And the response body errorMessage contains "already exists"

  Scenario: Get non-existent song returns 404
    When the client GETs "/songs/99999"
    Then the response status is 404
    And the response body errorMessage contains "not found"

  Scenario: Delete non-existent song returns empty ids list
    When the client DELETEs "/songs" with param id "99999"
    Then the response status is 200
    And the response ids list is empty
