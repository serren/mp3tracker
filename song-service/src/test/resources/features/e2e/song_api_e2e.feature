Feature: Song API end-to-end scenarios

  Scenario: Create song then retrieve it
    Given a song request with id 1001, name "Hotel California", artist "Eagles"
    When the song is created via POST "/songs"
    Then the HTTP status is 200
    And the response contains id 1001
    When the song is fetched via GET "/songs/1001"
    Then the HTTP status is 200
    And the response contains name "Hotel California"

  Scenario: Create song then delete it then confirm it is gone
    Given a song request with id 1002, name "Imagine", artist "John Lennon"
    When the song is created via POST "/songs"
    Then the HTTP status is 200
    When the song is deleted via DELETE "/songs?id=1002"
    Then the HTTP status is 200
    And a subsequent GET "/songs/1002" returns 404

  Scenario: POST with invalid year format returns 400
    Given a song request with id 1003 and invalid year "1800"
    When the song is created via POST "/songs"
    Then the HTTP status is 400
    And the response body contains errorMessage with "Validation error"
