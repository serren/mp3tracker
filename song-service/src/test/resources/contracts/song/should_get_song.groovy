import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "GET /songs/{id} returns full song metadata"
    request {
        method GET()
        url '/songs/1'
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            id      : 1,
            name    : "Bohemian Rhapsody",
            artist  : "Queen",
            album   : "A Night at the Opera",
            duration: "05:55",
            year    : "1975"
        ])
    }
}
