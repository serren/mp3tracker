import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "DELETE /songs?id=1 deletes an existing song and returns the deleted ids"
    request {
        method DELETE()
        urlPath('/songs') {
            queryParameters {
                parameter('id', '1')
            }
        }
    }
    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body([
            ids: [1]
        ])
    }
}
