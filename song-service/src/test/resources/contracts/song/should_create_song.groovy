import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "POST /songs creates song metadata and returns the resource id"
    request {
        method POST()
        url '/songs'
        headers {
            contentType(applicationJson())
        }
        body([
            id      : 999,
            name    : "Hotel California",
            artist  : "Eagles",
            album   : "Hotel California",
            duration: "06:30",
            year    : "1977"
        ])
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            id: 999
        ])
    }
}
