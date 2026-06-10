package com.example.storageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DeletedIdsResponse {

    private List<Long> ids;
}
