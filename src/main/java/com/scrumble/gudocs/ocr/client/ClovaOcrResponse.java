package com.scrumble.gudocs.ocr.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record ClovaOcrResponse(List<Image> images) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Image(String inferResult, List<Field> fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Field(String inferText, Boolean lineBreak) {
    }
}
